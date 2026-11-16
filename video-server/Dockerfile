# ===========================================
# vidkeep 后端镜像（多阶段构建）
# 构建：docker build -t vidkeep-server .
# ===========================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只拷贝 pom，预热依赖层，源码变更时不必重新下载依赖
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests clean package

FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai
WORKDIR /app
# pom 中 finalName=app，compose 里 app/worker 都用 app.jar 启动
COPY --from=build /build/target/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
