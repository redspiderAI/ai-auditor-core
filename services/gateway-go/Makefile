# Makefile for Gateway-Go

# 默认目标
.DEFAULT_GOAL := help

# 变量
BINARY_NAME=gateway-go
BUILD_DIR=build
TESTS_DIR=tests
DOCKER_IMAGE_NAME=gateway-go

# 帮助信息
help:
	@echo 可用的make命令:
	@echo   build          - 构建应用
	@echo   build-tests    - 构建测试工具
	@echo   run            - 运行应用
	@echo   run-grpc       - 运行应用(gRPC模式)
	@echo   test           - 运行所有测试
	@echo   test-verbose   - 运行详细测试
	@echo   clean          - 清理构建文件
	@echo   docker-build   - 构建Docker镜像
	@echo   docker-run     - 运行Docker容器
	@echo   deps           - 更新依赖

# 构建应用
build:
	@echo 构建 $(BINARY_NAME)...
	if not exist $(BUILD_DIR) mkdir $(BUILD_DIR)
	go build -o $(BUILD_DIR)\$(BINARY_NAME).exe .

# 构建测试工具
build-tests: build
	@echo 构建测试工具...
	go build -o $(BUILD_DIR)\check_service.exe $(TESTS_DIR)\check_service.go
	go build -o $(BUILD_DIR)\demo_client.exe $(TESTS_DIR)\demo_client.go
	go build -o $(BUILD_DIR)\test_json_interface.exe $(TESTS_DIR)\test_json_interface.go

# 运行应用
run: build
	@echo 运行 $(BINARY_NAME)...
	$(BUILD_DIR)\$(BINARY_NAME).exe

# 运行应用(gRPC模式)
run-grpc:
	@echo 运行 $(BINARY_NAME) (gRPC模式)...
	go run -tags grpc main.go

# 运行测试
test:
	@echo 运行测试...
	go test ./...

# 运行详细测试
test-verbose:
	@echo 运行详细测试...
	go test -v ./...

# 清理构建文件
clean:
	@echo 清理构建文件...
	if exist $(BUILD_DIR) rmdir /s /q $(BUILD_DIR)

# 构建Docker镜像
docker-build:
	@echo 构建Docker镜像 $(DOCKER_IMAGE_NAME)...
	docker build -t $(DOCKER_IMAGE_NAME) .

# 运行Docker容器
docker-run: docker-build
	@echo 运行Docker容器...
	docker run -p 8080:8080 $(DOCKER_IMAGE_NAME)

# 更新依赖
deps:
	@echo 更新依赖...
	go mod tidy
	go mod download

.PHONY: help build build-tests run run-grpc test test-verbose clean docker-build docker-run deps