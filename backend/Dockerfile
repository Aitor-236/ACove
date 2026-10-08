# ============================================================
# ACove 后端镜像：多阶段构建
#   1. builder  —— 用 Maven 镜像编译出可执行 jar
#   2. runtime  —— 只保留 JRE + jar，镜像更小、暴露面更小
# 构建（在仓库根目录执行）：
#   docker build -t acove-backend ./backend
# 运行时的数据库 / JWT 配置全部走环境变量，见 application-docker.yml
# 注意：这里刻意不写 `# syntax=docker/dockerfile:1`——那会让每次构建都去
# Docker Hub 拉 frontend 镜像，国内网络容易直接超时；BuildKit 内置前端已够用。
# ============================================================

# ---------- 1. 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先只拷 pom.xml：依赖没变时这一层直接命中缓存，改代码不用重新下载依赖
COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline || true

COPY src ./src
RUN mvn -B -DskipTests clean package

# ---------- 2. 运行阶段 ----------
FROM eclipse-temurin:21-jre AS runtime

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai" \
    BLOG_UPLOAD_DIR=/app/uploads

# 非 root 运行，容器被攻破时影响面更小；UID 交给系统分配——
# Temurin 基础镜像里已经有 UID 1000 的用户（Ubuntu 自带的 ubuntu），写死会冲突。
# /app/uploads 在这里先建好，挂载数据卷时目录属主会被继承。
RUN useradd --create-home --user-group app \
    && mkdir -p /app/uploads \
    && chown -R app:app /app

WORKDIR /app

COPY --from=builder --chown=app:app /build/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
