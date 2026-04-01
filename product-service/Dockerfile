FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY product-service/gradlew ./
COPY product-service/gradle ./gradle
COPY product-service/build.gradle.kts product-service/settings.gradle.kts ./
COPY product-service/src ./src
COPY hoppingmall-common /hoppingmall-common
COPY hoppingmall-idempotency /hoppingmall-idempotency
COPY hoppingmall-cache /hoppingmall-cache
COPY hoppingmall-dlq /hoppingmall-dlq
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8083
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8083/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
