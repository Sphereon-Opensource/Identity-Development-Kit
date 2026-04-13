FROM eclipse-temurin:21-jre

RUN groupadd --gid 10001 appuser && \
    useradd --uid 10001 --gid appuser --shell /bin/false --create-home appuser

WORKDIR /app

# Fat JAR must be pre-built: ./gradlew :services-oid4vp-verifier-rest:buildFatJar
COPY services/oid4vp-verifier/rest/build/libs/*-all.jar ./app.jar
COPY services/oid4vp-verifier/container/config/ ./config/

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
