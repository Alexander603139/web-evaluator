FROM gradle:8.10-jdk21 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle :app:bootJar --no-daemon

FROM mcr.microsoft.com/playwright/java:v1.49.0-noble
WORKDIR /app
COPY --from=build /home/gradle/project/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]