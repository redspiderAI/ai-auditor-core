# Multi-stage build: compile with Maven, run with JRE   1.构建阶段（使用 Maven 镜像）
FROM maven:3.9.4-eclipse-temurin-17 AS builder
WORKDIR /workspace

# copy maven config and sources   复制 Maven 配置和源代码
COPY pom.xml ./
COPY src ./src

# build the project artifact     构建项目（跳过测试）
RUN mvn -B -DskipTests package


#运行阶段配置
FROM eclipse-temurin:17-jre

# Default JVM options (can be overridden via docker run -e JAVA_OPTS)   JVM 参数
ENV JAVA_OPTS="-Xmx2g -Xms2g"

# Environment variables expected by the app (can be overridden at runtime)  应用环境变量
ENV JAVA_ENGINE_ADDR=engine-java:9191
ENV JAVA_GRPC_PORT=9192

# Expose both HTTP and gRPC ports (configurable via env)    端口
EXPOSE 9191 9192

# Copy built jar   从构建阶段复制生成的 JAR 文件
COPY --from=builder /workspace/target/engine-java-0.1.0-SNAPSHOT.jar /app/app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
