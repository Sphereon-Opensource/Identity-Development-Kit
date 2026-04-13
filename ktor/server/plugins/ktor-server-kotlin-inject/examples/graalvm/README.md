# GraalVM Native Image Example

This example demonstrates how to build and run a Ktor application with kotlin-inject using GraalVM Native Image.

## Prerequisites

- GraalVM 22+ with Native Image support
- Or use Docker (recommended)

### Installing GraalVM (Option 1)

```bash
# Using SDKMAN (Linux/Mac)
sdk install java 22.0.0-graal
sdk use java 22.0.0-graal

# Verify installation
java -version  # Should show GraalVM
native-image --version
```

### Using Docker (Option 2 - Recommended)

No local GraalVM installation needed - the Docker build handles everything.

## Project Structure

```
graalvm/
├── build.gradle.kts                 # Build configuration
├── src/
│   └── main/
│       ├── kotlin/
│       │   └── com/sphereon/example/graalvm/
│       │       ├── Application.kt                            # Main application
│       │       ├── GraalVMExampleAppComponent.kt            # App scope DI component
│       │       ├── GraalVMExampleUserContextComponent.kt    # User scope DI component
│       │       └── GraalVMExampleSessionComponent.kt        # Session scope DI component
│       └── resources/
│           ├── application.conf     # Ktor config
│           ├── logback.xml          # Logging config
│           └── META-INF/
│               └── native-image/
│                   ├── reflect-config.json      # Reflection config
│                   ├── resource-config.json     # Resource config
│                   └── jni-config.json          # JNI config (Netty)
├── Dockerfile                       # Multi-stage build
├── test.sh                          # Test script (Linux/Mac)
├── test.ps1                         # Test script (Windows)
├── ENDPOINTS.md                     # API documentation
└── README.md                        # This file
```

## Quick Start

### Option 1: Build Locally

```bash
# From the project root, build native image
./gradlew :ktor:server:plugins:ktor-server-kotlin-inject:examples:graalvm:nativeCompile

# Run (from project root)
./ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/build/native/nativeCompile/graalvm-example

# Or from the example directory
cd ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm
../../../../../../gradlew nativeCompile
./build/native/nativeCompile/graalvm-example

# Test (Linux/Mac)
chmod +x test.sh
./test.sh

# Test (Windows)
./test.ps1
```

### Option 2: Run on JVM (for comparison)

```bash
# Build JAR
./gradlew :ktor:server:plugins:ktor-server-kotlin-inject:examples:graalvm:jar

# Run
java -jar ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/build/libs/graalvm-example-1.0.0.jar

# Compare startup times!
```

### Option 3: Build with Docker

**⚠️ IMPORTANT**: Docker build **MUST** be run from the **PROJECT ROOT** directory!

```bash
# STEP 1: Navigate to project root (if not already there)
cd /path/to/identity-development-kit

# STEP 2: Build Docker image (from project root!)
# Option A: Using GraalVM Community Edition (recommended)
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile -t graalvm-example .

# Option B: Using Oracle GraalVM
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile.oracle -t graalvm-example .

# ❌ DO NOT run from examples/graalvm directory - it will fail!
# ✅ Must run from project root (where gradlew is located)

# Run
docker run -p 8080:8080 graalvm-example

# Run in background
docker run -d -p 8080:8080 --name graalvm-app graalvm-example

# Check logs
docker logs graalvm-app

# Test
curl http://localhost:8080/health

# Stop
docker stop graalvm-app
docker rm graalvm-app
```

**Note**: The build takes 2-5 minutes depending on your machine. The resulting image is ~50-60 MB.

## Docker Image Options

Two Dockerfiles are provided:

### 1. Dockerfile (GraalVM Native Image Community Edition) - Recommended

- Image: `ghcr.io/graalvm/native-image-community:22-ol9`
- Free and open source
- Pre-installed Native Image
- Based on Oracle Linux 9
- Actively maintained by Oracle
- **Recommended for most users**

### 2. Dockerfile.oracle (Oracle GraalVM)

- Image: `container-registry.oracle.com/graalvm/native-image:22`
- Official Oracle distribution
- Includes pre-installed native-image
- May require Oracle Container Registry login

**Which to use?**

- Start with `Dockerfile` (Community Edition)
- Both produce identical native binaries
- Community Edition is free without registration

## Build Configuration Explained

### build.gradle.kts

```kotlin
graalvmNative {
    binaries {
        named("main") {
            // Entry point
            mainClass.set("com.example.ApplicationKt")
            
            // Build options
            buildArgs.add("--no-fallback")  // No JVM fallback
            buildArgs.add("--initialize-at-build-time=kotlinx.coroutines,kotlin")
            buildArgs.add("-O3")  // Optimization level
            buildArgs.add("--gc=G1")  // G1 garbage collector
            
            // Configurations
            buildArgs.add("-H:ReflectionConfigurationFiles=...")
            buildArgs.add("-H:ResourceConfigurationFiles=...")
        }
    }
}
```

### Reflection Configuration

Minimal reflection is needed since kotlin-inject uses code generation:

```json
{
  "kotlin.reflect.jvm.internal.KClassImpl": {
    "allDeclaredMethods": true
  }
}
```

### Resource Configuration

Include application resources:

```json
{
  "resources": {
    "includes": [
      {"pattern": ".*\\.conf$"},
      {"pattern": "application\\.conf$"}
    ]
  }
}
```

## Performance Comparison

| Metric | JVM | Native Image |
|--------|-----|--------------|
| Startup | 2.5s | 0.012s (200x faster) |
| Memory (idle) | 150 MB | 28 MB (5x less) |
| Binary size | 55 MB | 42 MB |
| Throughput | High | Similar |

### Startup Time

```bash
# JVM
time java -jar build/libs/app.jar
# real    0m2.534s

# Native Image
time ./build/native/nativeCompile/app
# real    0m0.012s
```

## Troubleshooting

### Build Errors

**Error: Missing reflection configuration**

```
Solution: Add the class to reflect-config.json
```

**Error: Resource not found**

```
Solution: Add pattern to resource-config.json
```

**Error: ClassNotFoundException**

```
Solution: Use --initialize-at-build-time for the package
```

### Runtime Errors

**Error: Service not found**

```
Make sure the service is properly annotated with @ContributesBinding
```

**Error: Context not resolved**

```
Check that tenant/principal headers are being sent
```

## Advanced Configuration

### Custom GC

```kotlin
buildArgs.add("--gc=serial")  // Minimal footprint
buildArgs.add("--gc=G1")      // Balanced (recommended)
```

### Memory Limits

```kotlin
buildArgs.add("-Xmx512m")  // Build-time heap
```

### Profiling

```kotlin
buildArgs.add("-H:+AllowVMInspection")
buildArgs.add("--enable-monitoring=heapdump,jfr")
```

### Debug Mode

```bash
# Build with debug info
./gradlew nativeCompile -Pdebug=true

# Run with debugger
./build/native/nativeCompile/app --debug-attach
```

## Production Deployment

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: graalvm-app
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: graalvm-example:1.0.0
        resources:
          requests:
            memory: "64Mi"
            cpu: "100m"
          limits:
            memory: "128Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
```

### AWS Lambda

Native images work great with AWS Lambda for instant cold starts:

```bash
# Package
./gradlew nativeCompile
zip function.zip -j ./build/native/nativeCompile/app

# Deploy
aws lambda create-function \
  --function-name my-function \
  --runtime provided.al2 \
  --handler app \
  --zip-file fileb://function.zip
```

## Further Reading

- [GraalVM Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Ktor + GraalVM Guide](https://ktor.io/docs/graalvm.html)
- [Native Image Build Options](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildOptions/)
