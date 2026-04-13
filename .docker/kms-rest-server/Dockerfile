FROM eclipse-temurin:21-jdk AS builder

RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    findutils \
    wget \
    unzip \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app

RUN ./gradlew  :libraries:crypto:kms:rest:lib-crypto-kms-rest-server:bootJar \
    --no-daemon --parallel --build-cache \
    -Dkotlin.mpp.enabledTargets=jvm \
    -Dkotlin.native.ignoreDisabledTargets=true \
    -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g"

RUN find /app/libraries/crypto/kms/rest -name "*.jar" -type f

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/libraries/crypto/kms/rest/server/build/libs/kms-rest-server.jar ./kms-rest-server.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "kms-rest-server.jar"]