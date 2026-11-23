ARG JAR_SOURCE=build

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY consistency-monitor/gradlew ./
COPY consistency-monitor/gradle ./gradle
COPY consistency-monitor/build.gradle.kts consistency-monitor/settings.gradle.kts ./
COPY consistency-monitor/src ./src
RUN sed -i "s/\r$//" gradlew && chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM scratch AS jar-build
COPY --from=build /app/build/libs/*.jar /app.jar

FROM scratch AS jar-prebuilt
COPY consistency-monitor/build/libs/*.jar /app.jar

FROM jar-${JAR_SOURCE} AS jar

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=jar /app.jar app.jar
ENV JAVA_OPTS="-Xms128m -Xmx256m"
ENV SPRING_PROFILES_ACTIVE="docker"
EXPOSE 8087
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -qO- http://localhost:8087/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
