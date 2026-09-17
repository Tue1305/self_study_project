# Stage 1: Build the JAR using JDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml first to cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Grant execution rights on the Maven wrapper script
RUN chmod +x ./mvnw

# Download dependencies (this layer gets cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline

# Copy the rest of the source code and package the app
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Create lightweight runtime container with JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built jar file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose Spring Boot port
EXPOSE 8080

# Command to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]