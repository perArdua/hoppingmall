FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache libc6-compat
WORKDIR /app
COPY payment-service/gradlew ./
COPY payment-service/gradle ./gradle
COPY payment-service/build.gradle.kts payment-service/settings.gradle.kts ./
COPY payment-service/src ./src
COPY hoppingmall-common /hoppingmall-common
COPY hoppingmall-dlq /hoppingmall-dlq
COPY hoppingmall-outbox /hoppingmall-outbox
COPY hoppingmall-cache /hoppingmall-cache
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8085
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8085/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
