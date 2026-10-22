FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY consistency-monitor/gradlew ./
COPY consistency-monitor/gradle ./gradle
COPY consistency-monitor/build.gradle.kts consistency-monitor/settings.gradle.kts ./
COPY consistency-monitor/src ./src
COPY scripts/invariants /scripts/invariants
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
