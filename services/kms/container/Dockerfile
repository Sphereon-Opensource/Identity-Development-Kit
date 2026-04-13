FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    findutils \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app

RUN ./gradlew :services-kms-rest:buildFatJar \
    --no-daemon --parallel --build-cache \
    -Dkotlin.mpp.enabledTargets=jvm \
    -Dkotlin.native.ignoreDisabledTargets=true \
    -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"

FROM eclipse-temurin:21-jre

RUN groupadd --gid 10001 appuser && \
    useradd --uid 10001 --gid appuser --shell /bin/false --create-home appuser

WORKDIR /app

COPY --from=builder /app/services/kms/rest/build/libs/*-all.jar ./app.jar
COPY services/kms/container/config/ ./config/

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
