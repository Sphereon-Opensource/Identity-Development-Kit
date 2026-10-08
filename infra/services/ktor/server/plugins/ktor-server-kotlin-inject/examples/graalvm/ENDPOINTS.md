# GraalVM Example API Endpoints

This document describes all available endpoints in the GraalVM example application.

## Base URL

```
http://localhost:8080
```

## Endpoints

### 1. Root Endpoint

**GET /**

Simple welcome message.

```bash
curl http://localhost:8080/
```

Response:

```
Hello from GraalVM Native Image! 🚀
```

---

### 2. Health Check

**GET /health**

Returns application health status.

```bash
curl http://localhost:8080/health
```

Response:

```json
{
  "status": "UP",
  "nativeImage": true
}
```

---

### 3. Configuration

**GET /config**

Returns application configuration (demonstrates App-scoped service access).

```bash
curl http://localhost:8080/config
```

Response:

```json
{
  "appName": "graalvm-example",
  "profile": "production",
  "nativeImage": true
}
```

---

### 4. User Context

**GET /user/{userId}**

Demonstrates User-scoped service access with tenant/principal resolution.

**Headers:**

- `X-Tenant-ID`: Tenant identifier (e.g., "acme-corp")
- `X-User-ID`: User/principal identifier (e.g., "john@acme.com")

```bash
curl -H "X-Tenant-ID: acme-corp" \
     -H "X-User-ID: john@acme.com" \
     http://localhost:8080/user/john
```

Response:

```json
{
  "userId": "john",
  "tenant": "acme-corp",
  "principal": "john@acme.com",
  "contextId": "acme-corp:john@acme.com"
}
```

---

### 5. Session

**GET /session**

Demonstrates Session-scoped service access.

**Headers:**

- `X-Tenant-ID`: Tenant identifier
- `X-User-ID`: User/principal identifier

```bash
curl -H "X-Tenant-ID: acme-corp" \
     -H "X-User-ID: john@acme.com" \
     http://localhost:8080/session
```

Response:

```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "nativeImage": true
}
```

---

### 6. System Information

**GET /info**

Returns system runtime information (demonstrates Native Image vs JVM differences).

```bash
curl http://localhost:8080/info
```

Response (Native Image):

```json
{
  "nativeImage": true,
  "totalMemoryMB": 512,
  "freeMemoryMB": 480,
  "maxMemoryMB": 512,
  "processors": 8,
  "javaVersion": "22.0.0",
  "osName": "Linux",
  "osVersion": "5.15.0"
}
```

Response (JVM):

```json
{
  "nativeImage": false,
  "totalMemoryMB": 2048,
  "freeMemoryMB": 1800,
  "maxMemoryMB": 4096,
  "processors": 8,
  "javaVersion": "21.0.1",
  "osName": "Linux",
  "osVersion": "5.15.0"
}
```

---

## Testing All Endpoints

### Linux/Mac

```bash
chmod +x test.sh
./test.sh
```

### Windows (PowerShell)

```powershell
./test.ps1
```

### Manual Testing

```bash
# Health
curl http://localhost:8080/health | jq

# Config
curl http://localhost:8080/config | jq

# User with context
curl -H "X-Tenant-ID: acme-corp" \
     -H "X-User-ID: john@acme.com" \
     http://localhost:8080/user/john | jq

# Session
curl -H "X-Tenant-ID: acme-corp" \
     -H "X-User-ID: john@acme.com" \
     http://localhost:8080/session | jq

# System info
curl http://localhost:8080/info | jq
```

---

## Performance Comparison

### Startup Time

```bash
# JVM
time java -jar build/libs/graalvm-example-1.0.0.jar
# Typical: ~2-3 seconds

# Native Image
time ./build/native/nativeCompile/graalvm-example
# Typical: ~0.010-0.050 seconds (50-200x faster!)
```

### Memory Usage

```bash
# Check memory after startup
ps aux | grep graalvm-example

# JVM: ~150-200 MB
# Native: ~25-40 MB (5-6x less!)
```

### Load Testing

```bash
# Install wrk (if not available)
# Ubuntu: sudo apt install wrk
# Mac: brew install wrk

# Run load test
wrk -t4 -c100 -d30s http://localhost:8080/health

# Both JVM and Native Image achieve similar throughput
# JVM: ~50k req/s
# Native: ~45-48k req/s (90-96% of JVM)
```

---

## What Each Endpoint Demonstrates

| Endpoint | Demonstrates |
|----------|-------------|
| `/` | Basic routing |
| `/health` | Health checks, JSON serialization |
| `/config` | **App-scoped service** access via `getAppService<T>()` |
| `/user/{id}` | **User-scoped service** access via `getUserService<T>()` |
| `/session` | **Session-scoped service** access via `getSessionService<T>()` |
| `/info` | Runtime information, Native vs JVM differences |

---

## Key Observations

### Native Image Benefits

1. **Instant Startup**: 50-200x faster than JVM
2. **Low Memory**: 5-6x less memory usage
3. **Small Size**: Compact binaries
4. **No Warm-up**: Peak performance immediately

### What Works

✅ All three scopes (App/User/Session)
✅ Kotlin-inject service resolution  
✅ Ktor routing and content negotiation
✅ JSON serialization (kotlinx.serialization)
✅ Logging (Logback)
✅ Multi-tenant context resolution

### Limitations

⚠️ Slight throughput reduction (~4-10%)
⚠️ Longer build times (~2-5 minutes)
⚠️ Binary is platform-specific
