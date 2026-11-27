

FROM gradle:9.8.0-jdk25 AS build

USER root
WORKDIR /workspace
ENV GRADLE_USER_HOME=/gradle-cache

COPY gradle ./gradle
COPY build-logic ./build-logic
COPY libs/java ./libs/java
COPY services/schedule-service/settings.gradle.kts \
     services/schedule-service/build.gradle.kts \
     services/schedule-service/gradle.properties \
     ./services/schedule-service/

RUN --mount=type=cache,target=/gradle-cache,sharing=shared \
    gradle -p services/schedule-service dependencies \
      --no-daemon --console=plain --build-cache --configuration-cache

COPY services/schedule-service/src/main ./services/schedule-service/src/main

RUN --mount=type=cache,target=/gradle-cache,sharing=shared \
    gradle -p services/schedule-service bootJar \
      --no-daemon --console=plain --build-cache --configuration-cache 

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -g 1000 -S app \
    && adduser -u 999 -S -D -H -G app -h /app -s /bin/sh app \
    && mkdir -p /app \
    && chown -R app:app /app

WORKDIR /app

COPY --from=build --chown=app:app \
    /workspace/services/schedule-service/build/libs/*.jar /app/app.jar

USER app

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${MANAGEMENT_PORT:-8094}/actuator/health/readiness" || exit 1

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
