FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY settlement-service/gradlew ./
COPY settlement-service/gradle ./gradle
COPY settlement-service/build.gradle.kts settlement-service/settings.gradle.kts ./
COPY settlement-service/src ./src
COPY hoppingmall-common /hoppingmall-common
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
