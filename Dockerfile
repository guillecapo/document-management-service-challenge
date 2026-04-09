# ---- Stage 1: Build -------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

# Copy the POM first so Maven dependency downloads are cached as a separate
# layer. Subsequent builds only re-download if pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -Dspotless.check.skip=true -q

# ---- Stage 2: Runtime -----------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=builder /build/target/document-management-service-challenge-*.jar app.jar

# JVM flags matching the production memory budget in docker-compose.yml.
#   -Xmx50m          hard heap cap — service must stay within 50 MB
#   -Xms32m          initial heap; avoids lazy allocation overhead at startup
#   -XX:+UseSerialGC single-threaded GC, optimal for small-heap containers
#   -Xss256k         reduced thread stack — lowers per-thread memory footprint
#
# These defaults are intentionally identical to the JAVA_OPTS set in
# docker-compose so the service behaves the same whether started via
# 'docker-compose up' or 'docker run' without an explicit override.
ENV JAVA_OPTS="-Xmx50m -Xms32m -XX:+UseSerialGC -Xss256k"

EXPOSE 8080

# exec form via sh -c so JAVA_OPTS expansion works and the JVM receives
# SIGTERM directly (exec replaces the shell process).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
