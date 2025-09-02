package com.forge.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes
import org.slf4j.LoggerFactory

/**
 * Simplified OpenTelemetry integration for enhanced JSON-RPC daemon
 */
object ForgeTracing {
    private val logger = LoggerFactory.getLogger(ForgeTracing::class.java)
    private var openTelemetry: OpenTelemetry = OpenTelemetry.noop()
    private var tracer: Tracer = OpenTelemetry.noop().getTracer("forge")
    
    fun initialize(
        serviceName: String = "forge",
        serviceVersion: String = "1.0.0",
        otlpEndpoint: String? = null
    ) {
        logger.info("Initializing OpenTelemetry with service: $serviceName")
        
        val resource = Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, serviceName)
            .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
            .build()
        
        val sdkBuilder = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .apply {
                        val endpoint = otlpEndpoint 
                            ?: System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")
                            ?: System.getProperty("otel.exporter.otlp.endpoint")
                        
                        if (!endpoint.isNullOrBlank()) {
                            logger.info("Configuring OTLP exporter: $endpoint")
                            val exporter = OtlpGrpcSpanExporter.builder()
                                .setEndpoint(endpoint)
                                .build()
                            addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                        } else {
                            logger.info("No OTLP endpoint configured, spans will not be exported")
                        }
                    }
                    .build()
            )
        
        openTelemetry = sdkBuilder.build()
        tracer = openTelemetry.getTracer("forge", serviceVersion)
        
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("Shutting down OpenTelemetry")
            (openTelemetry as? OpenTelemetrySdk)?.shutdown()
        })
    }
    
    fun getTracer(): Tracer = tracer
    fun getOpenTelemetry(): OpenTelemetry = openTelemetry
}

/**
 * Extension function to execute code within a trace span
 */
suspend fun <T> withSpan(
    spanName: String,
    spanKind: SpanKind = SpanKind.INTERNAL,
    attributes: Attributes = Attributes.empty(),
    parentContext: Context? = null,
    block: suspend (span: Span) -> T
): T {
    val tracer = ForgeTracing.getTracer()
    val spanBuilder = tracer.spanBuilder(spanName)
        .setSpanKind(spanKind)
        .setAllAttributes(attributes)
    
    parentContext?.let { spanBuilder.setParent(it) }
    
    val span = spanBuilder.startSpan()
    
    return try {
        block(span)
    } catch (e: Throwable) {
        span.recordException(e)
        span.setStatus(StatusCode.ERROR, e.message ?: "Unknown error")
        throw e
    } finally {
        span.end()
    }
}