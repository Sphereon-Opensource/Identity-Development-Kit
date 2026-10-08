# Multi-stage Docker build for GraalVM Native Image
# 
# IMPORTANT: Must be built from the PROJECT ROOT with this example included in settings.gradle.kts
# Build command:
#   docker build -f ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/Dockerfile -t graalvm-example .

# Stage 1: Build with GraalVM Native Image
FROM ghcr.io/graalvm/native-image-community:22-ol9 AS build

# Install required tools
RUN microdnf install -y findutils git

WORKDIR /build

# Copy gradle wrapper and root build files
COPY gradlew gradlew.bat gradle.properties ./
COPY gradle/ gradle/

# Fix line endings and make executable (important for Windows hosts!)
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Copy root build configuration
COPY settings.gradle.kts build.gradle.kts ./

# Copy necessary source code
COPY libraries/ libraries/
COPY ktor/ ktor/

# Initialize git for version detection
RUN git config --global user.email "docker@build.local" && \
    git config --global user.name "Docker Build" && \
    git config --global init.defaultBranch main && \
    git init && \
    git add -A && \
    git commit -m "Docker build" || true

# Build the GraalVM example native image (disable config cache due to GraalVM plugin issues)
RUN ./gradlew :ktor:server:plugins:ktor-server-kotlin-inject:examples:graalvm:nativeCompile --no-daemon --no-configuration-cache --stacktrace

# Stage 2: Minimal runtime image
FROM debian:stable-slim

# Install minimal dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy native executable (GraalVM names it after the project, not the mainClass)
COPY --from=build /build/ktor/server/plugins/ktor-server-kotlin-inject/examples/graalvm/build/native/nativeCompile/graalvm /app/app

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Run the application
CMD ["/app/app"]
