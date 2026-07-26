# syntax=docker/dockerfile:1
# Multi-stage build for the "payment" multi-module Maven project.
# Build context MUST be the project root (where the parent pom.xml lives):
#   docker build -f app.dockerfile -t payment-app .

# ---------------------------------------------------------------------------
# Stage 1 — build the runnable Spring Boot jar
# ---------------------------------------------------------------------------
FROM maven:3.9.16-eclipse-temurin-25-alpine AS build
WORKDIR /home/app

# 1) Copy the parent pom + every module pom first, so the dependency layer is
#    cached and only re-resolved when a pom changes (not on every source edit).
#    All module poms are required for the aggregator reactor to load.
COPY pom.xml ./
COPY payment-submit/pom.xml             payment-submit/pom.xml
COPY payment-process/pom.xml            payment-process/pom.xml
COPY payment-bootstrap/pom.xml          payment-bootstrap/pom.xml
COPY payment-architecture-tests/pom.xml payment-architecture-tests/pom.xml

# 2) Warm the dependency cache (best-effort; the real build re-resolves anything
#    missing). The .m2 cache mount speeds up repeated builds.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl payment-bootstrap -am dependency:go-offline -DskipTests || true

# 3) Copy sources of the modules that actually go into the app.
COPY payment-submit/src    payment-submit/src
COPY payment-process/src   payment-process/src
COPY payment-bootstrap/src payment-bootstrap/src

# 4) Build only the runnable module and its dependencies (-pl ... -am), skipping
#    tests: the integration tests use Testcontainers, which needs a Docker daemon
#    that is not available during "docker build". The spring-boot plugin's
#    repackage goal is bound to the package phase, so this produces an executable jar.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl payment-bootstrap -am package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — slim runtime image
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine
LABEL maintainer="Gustavo Pontalti"

# Run as a non-root user
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

# The repackaged Spring Boot jar (payment-bootstrap, finalName = "payment")
COPY --from=build --chown=spring:spring /home/app/payment-bootstrap/target/payment.jar payment.jar

# Tomcat access log dir (server.tomcat.accesslog.directory=logs) must be writable
RUN mkdir -p /app/logs && chown -R spring:spring /app
USER spring

# 8080 = HTTP  |  8081 = actuator/management  |  8000 = remote debug (dev)
EXPOSE 8080 8081 8000

# Remote debugging — DEV ONLY. Remove or override (-e JAVA_TOOL_OPTIONS=) for production.
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000"

# Run with the "docker" profile so application-docker.yaml (service-name hosts) is used.
# Override at runtime with -e SPRING_PROFILES_ACTIVE=... if needed.
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", "-jar", "/app/payment.jar"]
