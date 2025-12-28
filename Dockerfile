# Step 1: Build the application
# Use Eclipse Temurin JDK 21 as the base image for building
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and configuration files
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies to cache them in the Docker layer
RUN ./mvnw dependency:go-offline

# Copy the source code and build the application JAR file
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Step 2: Create the runtime image
# Use a lightweight JRE image for running the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the compiled JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Copy the .env file as the application imports it for configuration
COPY .env .env

# Expose the default Spring Boot port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]