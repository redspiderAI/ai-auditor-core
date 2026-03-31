# ============================================================
# AI Auditor Engine-Java Dockerfile
# 多阶段构建：Maven 编译 → JRE 运行
# Java 版本：21（支持虚拟线程）
# ============================================================

# ------ 阶段一：构建（Maven + JDK 21）------
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /workspace

# 先只复制 pom.xml，利用 Docker 层缓存加速依赖下载
# （只有 pom.xml 变化时才重新下载依赖，源码变化不触发重新下载）
COPY pom.xml ./
RUN mvn -B dependency:go-offline -q

# 再复制源码并构建（跳过测试，测试在本地 mvn test 中单独运行）
COPY src ./src
RUN mvn -B -DskipTests package -q

# ------ 阶段二：运行（精简 JRE 21，体积更小）------
FROM eclipse-temurin:21-jre-jammy

# 创建非 root 用户运行（安全最佳实践）
RUN groupadd -r auditor && useradd -r -g auditor auditor

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /workspace/target/engine-java-0.1.0-SNAPSHOT.jar app.jar
RUN chown auditor:auditor app.jar

USER auditor

# 暴露端口
EXPOSE 8081
EXPOSE 9191

# JVM 参数（可通过 docker-compose.yml 的 JAVA_OPTS 环境变量覆盖）
ENV JAVA_OPTS="-Xmx1g -Xms512m -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
