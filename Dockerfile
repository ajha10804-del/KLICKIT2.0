FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and POM
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalize line endings and fix executable permissions for Linux
RUN sed -i 's/\r$//' ./mvnw && chmod +x ./mvnw

# Resolve dependencies
RUN ./mvnw dependency:go-offline -B

# Copy application sources and build artifact
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root system user for security
RUN addgroup -S klickit && adduser -S klickit -G klickit

COPY --from=builder /app/target/*.jar app.jar

RUN chown klickit:klickit app.jar

USER klickit

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
