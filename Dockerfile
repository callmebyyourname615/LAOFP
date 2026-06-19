# ── Stage 1: Dependency cache ─────────────────────────────────────────────────
# Copy pom.xml first so Maven downloads dependencies before copying source.
# This layer is cached and reused on subsequent builds when only src/ changes.
FROM maven:3.9.9-eclipse-temurin-21 AS deps
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw
RUN ./mvnw -q dependency:go-offline

# ── Stage 2: Build JAR ────────────────────────────────────────────────────────
# Tests are intentionally skipped here because integration tests use
# Testcontainers which requires a Docker daemon — not available inside
# a Docker build context. Tests must be run and pass in CI before this
# image is built (see .github/workflows/ci.yml job order).
FROM deps AS build
ARG BUILD_COMMIT=unknown
ARG BUILD_IMAGE=unknown
COPY src src
RUN ./mvnw -q clean package -DskipTests \
    -Dbuild.commit="${BUILD_COMMIT}" \
    -Dbuild.image="${BUILD_IMAGE}"

# ── Stage 3: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre AS runtime
ARG BUILD_COMMIT=unknown
ARG BUILD_IMAGE=unknown
LABEL org.opencontainers.image.title="Switching API" \
      org.opencontainers.image.revision="${BUILD_COMMIT}" \
      org.opencontainers.image.source="${BUILD_IMAGE}"
WORKDIR /app

# Run as non-root user (RISK-DEP-002)
RUN addgroup --system --gid 10001 switching \
    && adduser --system --uid 10001 --ingroup switching --no-create-home switching
USER switching

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:-} -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar"]
