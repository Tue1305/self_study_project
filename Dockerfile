# Stage 1: Build the app
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create runtime container
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy the generated JAR
COPY --from=builder /app/target/*.jar app.jar

# Explicitly copy static resources into the container directory
COPY src/main/resources/static /app/static

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]