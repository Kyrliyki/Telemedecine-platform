

FROM gradle:9.8.0-jdk25 AS build

USER root
WORKDIR /workspace
ENV GRADLE_USER_HOME=/gradle-cache

COPY gradle ./gradle
COPY build-logic ./build-logic
COPY libs/java ./libs/java
COPY services/emr-service/settings.gradle.kts \
     services/emr-service/build.gradle.kts \
     services/emr-service/gradle.properties \
     ./services/emr-service/

RUN --mount=type=cache,target=/gradle-cache,sharing=shared \
    gradle -p services/emr-service dependencies \
      --no-daemon --console=plain --build-cache --configuration-cache

COPY services/emr-service/src/main ./services/emr-service/src/main

RUN --mount=type=cache,target=/gradle-cache,sharing=shared \
    gradle -p services/emr-service bootJar \
      --no-daemon --console=plain --build-cache --configuration-cache 

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -g 1000 -S app \
    && adduser -u 999 -S -D -H -G app -h /app -s /bin/sh app \
    && mkdir -p /app \
    && chown -R app:app /app

WORKDIR /app

COPY --from=build --chown=app:app \
    /workspace/services/emr-service/build/libs/*.jar /app/app.jar

USER app

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-8095}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=50", \
    "-XX:InitialRAMPercentage=20", \
    "-XX:MaxMetaspaceSize=160m", \
    "-XX:ReservedCodeCacheSize=64m", \
    "-XX:MaxDirectMemorySize=64m", \
    "-XX:+UseSerialGC", \
    "-XX:ActiveProcessorCount=2", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-jar", "/app/app.jar"]
