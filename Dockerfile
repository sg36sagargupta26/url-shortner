# ── Stage 1: Build ──
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build scripts first for dependency caching
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY gradle/ gradle/

# Download dependencies (cached layer unless build scripts change)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q

# Copy source and build the bootJar
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ── Stage 2: Runtime ──
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S shortly && adduser -S shortly -G shortly

WORKDIR /app

# Copy the Spring Boot fat jar from the build stage
COPY --from=builder /workspace/build/libs/*.jar shortly.jar

# MaxMind GeoLite2 DB placeholder — mount or download in CI
RUN mkdir -p /app/data

USER shortly

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:MaxRAMPercentage=75", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "shortly.jar"]
