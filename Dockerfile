# 基础镜像源前缀，可用 --build-arg REGISTRY=docker.io/library 切换回官方源
ARG REGISTRY=docker.io/library

# ---------- Stage 1: 构建前端 ----------
FROM ${REGISTRY}/node:20-alpine AS frontend-build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- Stage 2: 构建后端（前端产物打入 jar 的 static 目录） ----------
FROM ${REGISTRY}/maven:3.9-eclipse-temurin-8 AS backend-build
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline || true
COPY backend/src ./src
COPY --from=frontend-build /build/frontend/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

# ---------- Stage 3: 运行镜像 ----------
FROM ${REGISTRY}/eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=backend-build /build/backend/target/json-excel-backend-*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
