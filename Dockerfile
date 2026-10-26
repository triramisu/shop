# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY ci ci
RUN chmod +x mvnw

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# CVE-2026-111497: refresh the decoder before the next Temurin base rebuild.
RUN apk upgrade --no-cache brotli-libs \
    && addgroup --system spring \
    && adduser --system --disabled-password --no-create-home --uid 10001 --ingroup spring spring
COPY --from=build /workspace/target/*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
# JVM nhận biết cgroup: giới hạn RAM của container (mem_limit) sẽ bó heap theo
# tỉ lệ, nhờ đó môi trường lab có biên tài nguyên rõ ràng. Có thể override qua
# JAVA_OPTS khi chạy.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError"
USER 10001
EXPOSE 8080
# Busybox wget có sẵn trong image alpine; healthcheck dựa trên actuator health.
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
# sh -c + exec: giữ Java là PID 1 (nhận tín hiệu dừng) mà vẫn chèn được JAVA_OPTS.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
