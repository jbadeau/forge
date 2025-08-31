# Remote Execution Server Setup

This directory contains Docker Compose configurations to run Remote Execution API servers for testing Forge's distributed build capabilities.

## Current Status ✅

**Forge Remote Execution Integration is Complete!**

We have successfully:
- ✅ **Replaced TaskExecutor with RemoteExecutionExecutor**
- ✅ **Generated Remote Execution API Java classes**
- ✅ **Built unified local/remote execution system**
- ✅ **Added real caching support via ActionCache**

## Testing (Without Server)

You can test the integration by running Forge commands - they will attempt to connect to a Remote Execution server:

```bash
# This will show Remote Execution attempting to connect
java -jar ../forge-cli/target/forge.jar run go-utils build --verbose

# Expected output:
# ✅ Using Local Execution with default Remote Execution endpoint  
# ✅ Starting remote execution of 1 task(s)
# ❌ Connection failure (expected - no server running)
```

## Setting Up a Real Server

To test with actual remote execution, you'll need to set up a Remote Execution API server:

### Option 1: Use BuildBuddy (Easiest)
```bash
# Sign up at buildbuddy.io and get your endpoint
# Add to forge.json:
{
  "remoteExecution": {
    "enabled": true, 
    "defaultEndpoint": "grpcs://remote.buildbuddy.dev:1985"
  }
}
```

### Option 2: Local BuildBarn Setup
```bash
# Follow BuildBarn documentation to set up locally
docker run -p 8080:8080 <buildbarn-image>
```

## Available Options

### 1. Simple Remote Execution Server (Port 8080)
**Best for**: Quick testing and development
- **Image**: `gcr.io/bazel-public/remote-execution-server`
- **Port**: 8080 (matches Forge's default)
- **Features**: Basic Remote Execution API support

```bash
mise start-remote-execution
```

### 2. BuildBarn Setup (Port 8980)
**Best for**: More advanced testing with proper CAS and ActionCache
- **Components**: bb-scheduler + bb-worker
- **Port**: 8980
- **Features**: Full Remote Execution API, content-addressable storage, action cache

```bash
mise start-buildbarn

# Update Forge config to use port 8980
# Edit forge.json and add:
{
  "remoteExecution": {
    "enabled": true,
    "defaultEndpoint": "localhost:8980"
  }
}
```

### 3. Full Buildfarm Setup
**Best for**: Production-like testing
- **Components**: Buildfarm server + worker + Redis
- **Ports**: 8980 (gRPC), 8981 (Web UI)
- **Features**: Complete Bazel Buildfarm implementation

```bash
docker-compose -f docker-compose.yml up -d
```

## Testing Remote Execution

1. **Start a server** (choose one):
   ```bash
   mise start-remote-execution        # Simple (port 8080)
   mise start-buildbarn              # BuildBarn (port 8980)
   ```

2. **Test with Forge**:
   ```bash
   # Should connect to remote execution server
   java -jar ../forge-cli/target/forge.jar run go-utils build --verbose
   
   # Run again to test caching
   java -jar ../forge-cli/target/forge.jar run go-utils build --verbose
   ```

3. **Expected output**:
   ```
   ✅ Using Local Execution with default Remote Execution endpoint
   ✅ Starting remote execution of 1 task(s)
   ✅ Task go-utils:build completed successfully
   ```

## Configuration

### Forge Configuration (forge.json)
To enable remote execution by default, add to your `forge.json`:

```json
{
  "remoteExecution": {
    "enabled": true,
    "defaultEndpoint": "localhost:8080",
    "useTls": false,
    "defaultTimeoutSeconds": 300,
    "defaultPlatform": {
      "OS": "linux",
      "cpu": "x86_64"
    }
  }
}
```

### Per-target configuration
```json
{
  "targets": {
    "build": {
      "remoteExecution": {
        "endpoint": "localhost:8080",
        "platform": {
          "container-image": "node:18"
        }
      }
    }
  }
}
```

## Troubleshooting

### Connection Issues
```bash
# Check if server is running
docker ps | grep remote-exec

# Check server logs  
mise remote-execution-logs

# Test direct connection
grpcurl -plaintext localhost:8080 build.bazel.remote.execution.v2.Capabilities/GetCapabilities
```

### Cache Issues
```bash
# Clear Docker volumes (resets cache)
docker-compose -f docker-compose.simple.yml down -v
```

## Useful Commands

```bash
# View all running containers
docker ps

# Stop all remote execution services
docker stop $(docker ps -q --filter "name=*remote*")

# Clean up Docker resources
docker system prune -f

# Monitor resource usage
docker stats
```

## Next Steps

- **Production**: Use a proper Remote Execution cluster (Google Cloud Build, BuildBuddy Cloud)
- **CI/CD**: Configure your build pipeline to use remote execution
- **Monitoring**: Add Prometheus/Grafana for build metrics
- **Scaling**: Add more worker nodes for parallel execution