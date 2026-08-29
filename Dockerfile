# Use official lightweight Java Runtime
FROM eclipse-temurin:17-jre-alpine

# Set working directory inside container
WORKDIR /app

# Copy executable jar from target folder into container
COPY target/*.jar app.jar

# Expose Spring Boot port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]