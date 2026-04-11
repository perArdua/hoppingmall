FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY order-service/gradlew ./
COPY order-service/gradle ./gradle
COPY order-service/build.gradle.kts order-service/settings.gradle.kts ./
COPY order-service/src ./src
COPY hoppingmall-common /hoppingmall-common
COPY hoppingmall-idempotency /hoppingmall-idempotency
COPY hoppingmall-dlq /hoppingmall-dlq
COPY hoppingmall-outbox /hoppingmall-outbox
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
