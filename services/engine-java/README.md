# Engine-Java

Java 模块（规则引擎）初始化说明

目标：使用 Maven 管理，构建时自动从 `shared/protos` 生成 Java POJO（通过 `protoc-jar-maven-plugin`），并成为可运行的 Spring Boot 服务。

快速上手

1. 在仓库根目录运行 Maven 构建（会在 generate-sources 阶段调用 protoc-jar）：

```bash
cd services/engine-java
mvn clean package
```

1. 如果成功，生成的 protobuf Java 源会放在：

```
services/engine-java/target/generated-sources/protobuf/java
```

1. 运行服务（本地）：

```bash
mvn spring-boot:run
# 或运行打包后的 jar
java -jar target/engine-java-0.1.0-SNAPSHOT.jar
```

注意

- `pom.xml` 使用 `protoc-jar-maven-plugin` 在 `generate-sources` 阶段运行 `protoc`，从 `../shared/protos` 中读取 `.proto` 文件并生成 Java 源。
- 如果需要同时生成 gRPC stub（`grpc-java`），需要为 `protoc` 提供 `protoc-gen-grpc-java` 插件并在 `protocArgs` 中指定 `--grpc-java_out`；当前配置至少会生成 Java POJO。

如需我把 gRPC 代码生成也加入到构建流程（并示例集成 gRPC server stub），我可以继续补充。
