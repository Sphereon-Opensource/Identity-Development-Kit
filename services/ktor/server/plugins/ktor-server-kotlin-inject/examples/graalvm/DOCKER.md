# Docker Build Guide

This guide explains how to build and run the GraalVM example using Docker.

## Available Dockerfiles

### 1. `Dockerfile` - GraalVM Community Edition (Recommended)

```bash
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example .
```

**Base Image**: `ghcr.io/graalvm/jdk-community:22`

- Free and open source
- No registration required
- Actively maintained

### 2. `Dockerfile.oracle` - Oracle GraalVM

```bash
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile.oracle \
  -t graalvm-example .
```

**Base Image**: `container-registry.oracle.com/graalvm/native-image:22`

- Official Oracle distribution
- Pre-installed native-image
- May require registry login

## Quick Start

```bash
# 1. Build (from project root)
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example .

# 2. Run
docker run -p 8080:8080 graalvm-example

# 3. Test (in another terminal)
curl http://localhost:8080/health
```

## Build Options

### Standard Build

```bash
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example .
```

### Build with No Cache

```bash
docker build --no-cache \
  -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example .
```

### Build with Custom Tag

```bash
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t myapp:1.0.0-native .
```

## Running the Container

### Foreground (with logs)

```bash
docker run -p 8080:8080 graalvm-example
```

### Background (daemon mode)

```bash
docker run -d -p 8080:8080 --name graalvm-app graalvm-example

# View logs
docker logs graalvm-app

# Follow logs
docker logs -f graalvm-app

# Stop
docker stop graalvm-app
docker rm graalvm-app
```

### With Environment Variables

```bash
docker run -p 8080:8080 \
  -e APP_PROFILE=production \
  -e PORT=8080 \
  graalvm-example
```

### With Custom Port

```bash
# Run on host port 3000, container port 8080
docker run -p 3000:8080 graalvm-example

# Test
curl http://localhost:3000/health
```

### With Volume Mount (for logs)

```bash
docker run -p 8080:8080 \
  -v $(pwd)/logs:/app/logs \
  graalvm-example
```

## Image Details

### Size Comparison

```bash
# Check image size
docker images graalvm-example

# Typical sizes:
# graalvm-example    ~50-60 MB
# JVM equivalent     ~200-250 MB (with JRE)
```

### Inspect the Image

```bash
# View image details
docker inspect graalvm-example

# View layers
docker history graalvm-example
```

### Run Shell Inside Container

```bash
# Start container with shell
docker run -it --entrypoint /bin/sh graalvm-example

# Or exec into running container
docker exec -it graalvm-app /bin/sh
```

## Performance Comparison

### Startup Time

```bash
# Native Image (this example)
docker run -p 8080:8080 graalvm-example
# → Ready in ~10-50ms

# JVM equivalent
docker run -p 8080:8080 jvm-example
# → Ready in ~2-3 seconds

# Native is 50-200x faster! ⚡
```

### Memory Usage

```bash
# Check memory usage
docker stats graalvm-app

# Native Image: ~30-40 MB
# JVM:          ~150-200 MB

# Native uses 5-6x less memory! 💾
```

## Troubleshooting

### Build Errors

#### Error: Image not found

```
ERROR: failed to resolve source metadata for ghcr.io/graalvm/...
```

**Solution**: Use alternative Dockerfile

```bash
docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile.oracle \
  -t graalvm-example .
```

#### Error: Out of memory during build

```
The process was killed due to insufficient memory
```

**Solution**: Increase Docker memory limit

- Docker Desktop → Settings → Resources → Memory → 8 GB+

#### Error: Build timeout

```
Gradle build exceeded timeout
```

**Solution**: Increase build timeout or disable it

```bash
docker build --no-cache \
  -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example . \
  --progress=plain
```

### Runtime Errors

#### Error: Port already in use

```
bind: address already in use
```

**Solution**: Use different port

```bash
docker run -p 3000:8080 graalvm-example
```

Or stop conflicting container:

```bash
docker ps
docker stop <container-id>
```

#### Error: Cannot connect to container

**Solution**: Check container is running

```bash
docker ps
docker logs graalvm-app
```

#### Error: Health check failing

**Solution**: Check application logs

```bash
docker logs graalvm-app

# If curl is missing
docker exec -it graalvm-app /bin/sh
# Check if curl is installed
which curl
```

## Production Deployment

### Docker Compose

```yaml
version: '3.8'

services:
  app:
    image: graalvm-example:1.0.0
    ports:
      - "8080:8080"
    environment:
      - APP_PROFILE=production
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 5s
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: graalvm-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: graalvm-app
  template:
    metadata:
      labels:
        app: graalvm-app
    spec:
      containers:
      - name: app
        image: graalvm-example:1.0.0
        ports:
        - containerPort: 8080
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
          initialDelaySeconds: 5
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 2
          periodSeconds: 10
```

### Push to Registry

```bash
# Tag for registry
docker tag graalvm-example myregistry.com/graalvm-example:1.0.0

# Push
docker push myregistry.com/graalvm-example:1.0.0

# Pull and run on another machine
docker pull myregistry.com/graalvm-example:1.0.0
docker run -p 8080:8080 myregistry.com/graalvm-example:1.0.0
```

## CI/CD Integration

### GitHub Actions

```yaml
name: Build Native Image

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Build Docker image
      run: |
        docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
          -t graalvm-example:${{ github.sha }} .
    
    - name: Test
      run: |
        docker run -d -p 8080:8080 --name test graalvm-example:${{ github.sha }}
        sleep 5
        curl -f http://localhost:8080/health
        docker stop test
```

## Performance Tuning

### Smaller Images

Use Alpine base:

```dockerfile
FROM alpine:3.18
RUN apk add --no-cache libc6-compat
COPY --from=build /build/.../graalvm-example /app/app
CMD ["/app/app"]
```

### Multi-Architecture

Build for multiple platforms:

```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile \
  -t graalvm-example .
```

## Further Reading

- [Docker Multi-Stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [GraalVM Container Images](https://www.graalvm.org/latest/docs/getting-started/container-images/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
