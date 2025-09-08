package com.frontseat.task.scheduling

import com.frontseat.task.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.coroutines.coroutineContext

/**
 * High-performance ready-queue scheduler with work-stealing for task execution.
 * Implements the scheduling strategy from "Lifecycle with Local DAGs".
 */
class ReadyQueueScheduler(
    private val options: SchedulerOptions = SchedulerOptions()
) {
    private val logger = LoggerFactory.getLogger(ReadyQueueScheduler::class.java)
    
    // Task state tracking
    private val taskStates = ConcurrentHashMap<TaskId, TaskState>()
    private val completedTasks = ConcurrentHashMap<TaskId, TaskResult>()
    
    // Ready queue with priority ordering
    private val readyQueue = PriorityBlockingQueue<ScheduledTask>(
        100,
        compareBy(
            { -it.priority },      // Higher priority first
            { it.phaseIndex },     // Earlier phases first (if soft barriers enabled)
            { it.task.id.value }   // Stable tie-breaker
        )
    )
    
    // Work-stealing queues per worker
    private val workerQueues = Array(options.maxWorkers) { 
        Channel<ScheduledTask>(Channel.UNLIMITED)
    }
    
    // Worker management
    private val activeWorkers = AtomicInteger(0)
    private val mutex = Mutex()
    
    /**
     * Execute a task graph with ready-queue scheduling
     */
    suspend fun execute(
        taskGraph: TaskGraph,
        executor: TaskExecutor
    ): ExecutionResults = coroutineScope {
        val startTime = Instant.now()
        
        // Initialize task states
        initializeTaskStates(taskGraph)
        
        // Calculate critical paths if enabled
        if (options.enableCriticalPath) {
            calculateCriticalPaths(taskGraph)
        }
        
        // Start worker coroutines
        val workers = List(options.maxWorkers) { workerId ->
            launch {
                workerLoop(workerId, executor)
            }
        }
        
        // Seed initial ready tasks
        updateReadyQueue(taskGraph)
        
        // Wait for all tasks to complete
        while (!isExecutionComplete()) {
            delay(10) // Small delay to prevent busy waiting
        }
        
        // Cancel workers
        workers.forEach { it.cancel() }
        
        val endTime = Instant.now()
        val duration = endTime.toEpochMilli() - startTime.toEpochMilli()
        
        // Build results
        return@coroutineScope ExecutionResults(
            results = completedTasks.mapKeys { it.key.value },
            totalDuration = duration,
            successCount = completedTasks.values.count { it.isSuccess },
            failureCount = completedTasks.values.count { it.isFailure }
        )
    }
    
    /**
     * Worker coroutine that processes tasks
     */
    private suspend fun workerLoop(
        workerId: Int,
        executor: TaskExecutor
    ) {
        while (coroutineContext.isActive) {
            // Try to get task from own queue first (work-stealing)
            var task = workerQueues[workerId].tryReceive().getOrNull()
            
            // If no task in own queue, try ready queue
            if (task == null) {
                task = readyQueue.poll()
            }
            
            // If still no task, try stealing from other workers
            if (task == null && options.enableWorkStealing) {
                task = stealWork(workerId)
            }
            
            if (task != null) {
                executeTask(task, executor, workerId)
            } else {
                // No work available, yield to other coroutines
                yield()
            }
        }
    }
    
    /**
     * Execute a single task
     */
    private suspend fun executeTask(
        scheduledTask: ScheduledTask,
        executor: TaskExecutor,
        workerId: Int
    ) {
        val task = scheduledTask.task
        val startTime = Instant.now()
        
        activeWorkers.incrementAndGet()
        
        try {
            // Update task state
            taskStates[task.id] = TaskState.RUNNING
            
            logger.debug("Worker $workerId executing task: ${task.id.value}")
            
            // Execute the task
            val result = executor.execute(task)
            
            val endTime = Instant.now()
            
            // Record completion
            val taskResult = TaskResult(
                task = task,
                status = if (result.success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                startTime = startTime,
                endTime = endTime,
                output = result.output,
                error = result.error,
                exitCode = result.exitCode,
                fromCache = result.fromCache
            )
            
            completedTasks[task.id] = taskResult
            taskStates[task.id] = if (result.success) TaskState.COMPLETED else TaskState.FAILED
            
            // Update ready queue with newly unblocked tasks
            if (result.success) {
                updateReadyQueueForCompletedTask(task)
            }
            
            // Task completed successfully
            
        } catch (e: Exception) {
            logger.error("Error executing task ${task.id.value}", e)
            taskStates[task.id] = TaskState.FAILED
            
            val endTime = Instant.now()
            completedTasks[task.id] = TaskResult(
                task = task,
                status = TaskStatus.FAILED,
                startTime = startTime,
                endTime = endTime,
                error = e.message ?: "Unknown error"
            )
        } finally {
            activeWorkers.decrementAndGet()
        }
    }
    
    /**
     * Try to steal work from other worker queues
     */
    private suspend fun stealWork(thiefWorkerId: Int): ScheduledTask? {
        // Try to steal from workers in round-robin fashion
        for (i in 1 until options.maxWorkers) {
            val targetWorker = (thiefWorkerId + i) % options.maxWorkers
            val stolen = workerQueues[targetWorker].tryReceive().getOrNull()
            if (stolen != null) {
                logger.debug("Worker $thiefWorkerId stole work from $targetWorker")
                return stolen
            }
        }
        return null
    }
    
    /**
     * Update ready queue with tasks whose dependencies are satisfied
     */
    private suspend fun updateReadyQueue(taskGraph: TaskGraph) = mutex.withLock {
        taskGraph.getAllTasks().forEach { task ->
            if (isTaskReady(task, taskGraph)) {
                val priority = calculatePriority(task, taskGraph)
                val phaseIndex = getPhaseIndex(task)
                
                val scheduledTask = ScheduledTask(
                    task = task,
                    priority = priority,
                    phaseIndex = phaseIndex
                )
                
                readyQueue.offer(scheduledTask)
                taskStates[task.id] = TaskState.READY
            }
        }
    }
    
    /**
     * Update ready queue after a task completes
     */
    private suspend fun updateReadyQueueForCompletedTask(completedTask: Task) = mutex.withLock {
        // Find tasks that depend on the completed task
        val taskGraph = TaskGraph() // This should be passed in or stored
        
        taskGraph.getSuccessors(completedTask.id).forEach { successorId ->
            val successor = taskGraph.getTask(successorId)
            if (successor != null && isTaskReady(successor, taskGraph)) {
                val priority = calculatePriority(successor, taskGraph)
                val phaseIndex = getPhaseIndex(successor)
                
                val scheduledTask = ScheduledTask(
                    task = successor,
                    priority = priority,
                    phaseIndex = phaseIndex
                )
                
                // Distribute to worker queues for better locality
                if (options.enableWorkStealing) {
                    val targetWorker = successorId.hashCode() % options.maxWorkers
                    workerQueues[targetWorker].trySend(scheduledTask)
                } else {
                    readyQueue.offer(scheduledTask)
                }
                
                taskStates[successor.id] = TaskState.READY
            }
        }
    }
    
    /**
     * Check if a task is ready to execute
     */
    private fun isTaskReady(task: Task, taskGraph: TaskGraph): Boolean {
        if (taskStates[task.id] != TaskState.PENDING) {
            return false
        }
        
        // Check if all dependencies are completed
        return task.dependencies.all { depId ->
            taskStates[depId] == TaskState.COMPLETED
        }
    }
    
    /**
     * Calculate priority for a task (critical path + other factors)
     */
    private fun calculatePriority(task: Task, taskGraph: TaskGraph): Int {
        var priority = 0
        
        if (options.enableCriticalPath) {
            // Use pre-calculated critical path score
            priority += criticalPathScores[task.id] ?: 0
        }
        
        // Boost priority for tasks with many dependents
        priority += min(taskGraph.getSuccessors(task.id).size * 10, 100)
        
        // Consider task type for priority
        priority += when (task.target) {
            "compile" -> 50
            "test" -> 100
            "package" -> 30
            else -> 10
        }
        
        return priority
    }
    
    /**
     * Get phase index for soft barrier ordering
     */
    private fun getPhaseIndex(task: Task): Int {
        return when (task.target) {
            "clean" -> 0
            "compile" -> 1
            "test" -> 2
            "package" -> 3
            "publish" -> 4
            "deploy" -> 5
            else -> 99
        }
    }
    
    // Critical path calculation
    private val criticalPathScores = ConcurrentHashMap<TaskId, Int>()
    
    private fun calculateCriticalPaths(taskGraph: TaskGraph) {
        // Calculate critical path scores using DFS
        val visited = mutableSetOf<TaskId>()
        val scores = mutableMapOf<TaskId, Int>()
        
        fun calculateScore(taskId: TaskId): Int {
            if (taskId in visited) {
                return scores[taskId] ?: 0
            }
            
            visited.add(taskId)
            val task = taskGraph.getTask(taskId) ?: return 0
            
            // Base score from estimated duration
            val baseDuration = when (task.target) {
                "compile" -> 5000
                "test" -> 10000
                "package" -> 3000
                else -> 1000
            }
            
            // Add maximum score from successors
            val successorScore = taskGraph.getSuccessors(taskId)
                .maxOfOrNull { calculateScore(it) } ?: 0
            
            val totalScore = baseDuration + successorScore
            scores[taskId] = totalScore
            criticalPathScores[taskId] = totalScore
            
            return totalScore
        }
        
        // Calculate scores for all tasks
        taskGraph.getAllTasks().forEach { task ->
            calculateScore(task.id)
        }
    }
    
    // Task state management
    private fun initializeTaskStates(taskGraph: TaskGraph) {
        taskGraph.getAllTasks().forEach { task ->
            taskStates[task.id] = TaskState.PENDING
        }
    }
    
    private fun isExecutionComplete(): Boolean {
        return taskStates.values.all { state ->
            state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.SKIPPED
        }
    }
}

/**
 * Task executor interface
 */
interface TaskExecutor {
    suspend fun execute(task: Task): ExecutionResult
}

/**
 * Result from executing a task
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String = "",
    val error: String = "",
    val exitCode: Int = 0,
    val fromCache: Boolean = false
)

/**
 * Scheduled task with priority information
 */
data class ScheduledTask(
    val task: Task,
    val priority: Int,
    val phaseIndex: Int
)

/**
 * Task execution state
 */
enum class TaskState {
    PENDING,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Scheduler configuration options
 */
data class SchedulerOptions(
    val maxWorkers: Int = Runtime.getRuntime().availableProcessors(),
    val enableWorkStealing: Boolean = true,
    val enableCriticalPath: Boolean = true,
    val enableSoftBarriers: Boolean = false,
    val taskTimeout: Long = 600_000, // 10 minutes
    val retryPolicy: RetryPolicy = RetryPolicy.NONE
)

/**
 * Retry policy for failed tasks
 */
enum class RetryPolicy {
    NONE,
    IMMEDIATE,
    EXPONENTIAL_BACKOFF
}

// Metrics class removed - keeping scheduler simple
