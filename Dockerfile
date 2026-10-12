# syntax=docker/dockerfile:1

# Builds the store service from source so `docker build .` gives the same image on any machine (#11).
# Stage 1 compiles with the committed Gradle wrapper on the JDK the build's toolchain declares, so nothing is
# downloaded beyond dependencies. Stage 2 is a JRE with the boot jar split into its layers, so a code change
# re-ships the application layer only, not the dependencies below it.

FROM eclipse-temurin:17-jdk AS builder
WORKDIR /workspace

# Resolve dependencies as their own layer: it is rebuilt only when the build files change, not on every source edit.
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null

# Tests are not run here; CI runs `./gradlew check` before it builds the image, and a local build should not need
# Docker-in-Docker for the Testcontainers suite.
COPY src ./src
RUN ./gradlew --no-daemon --quiet bootJar \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination extracted --launcher

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Alpine's busybox provides wget for the HEALTHCHECK; the Ubuntu-based JRE image ships neither wget nor curl.
RUN addgroup -S store && adduser -S store -G store
USER store

COPY --from=builder --chown=store:store /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=store:store /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=store:store /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=store:store /workspace/extracted/application/ ./

EXPOSE 8080

# UP from the actuator means the web server is accepting requests, Liquibase has finished (it runs before the server
# starts) and the DataSource ping succeeded. The start period covers migration time on a cold database.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

# Exec form so the JVM is PID 1 and receives SIGTERM directly. JVM flags go in JDK_JAVA_OPTIONS, which the launcher
# reads; the database connection comes from SPRING_DATASOURCE_URL, _USERNAME and _PASSWORD (see README).
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
