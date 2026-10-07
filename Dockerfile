# Multi-stage Dockerfile for Spring Boot gRPC Chat Application
# Stage 1: Build the application with Maven
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy Maven POM and source files
COPY pom.xml .
COPY src ./src

# Compile protobuf, compile code, and package executable jar
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime environment
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy packaged JAR from builder stage
COPY --from=builder /build/target/bidirectional-streaming-*.jar app.jar

# Expose REST (8080) and gRPC (9090) ports
EXPOSE 8080
EXPOSE 9090

# Run Spring Boot application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
