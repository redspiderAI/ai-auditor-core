# Multi-stage build: compile with Maven, run with JRE
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /workspace

# copy maven config and sources
COPY pom.xml ./
COPY src ./src

# build the project artifact
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre

# Default JVM options (can be overridden via docker run -e JAVA_OPTS)
ENV JAVA_OPTS="-Xmx2g -Xms2g"

# Environment variables expected by the app (can be overridden at runtime)
ENV JAVA_ENGINE_ADDR=engine-java:9191
ENV JAVA_GRPC_PORT=9192

# Expose both HTTP and gRPC ports (configurable via env)
EXPOSE 9191 9192

# Copy built jar
COPY --from=builder /workspace/target/engine-java-0.1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
