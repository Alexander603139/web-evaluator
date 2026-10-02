# Build stage
FROM gradle:9.7.1-jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN ./gradlew :app:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /home/gradle/project/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
