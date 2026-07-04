# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Warm the dependency cache first (uses the Aliyun mirror settings for fast, reliable builds).
COPY pom.xml settings.xml ./
RUN mvn -s settings.xml -B dependency:go-offline -DskipTests || true
COPY src ./src
RUN mvn -s settings.xml -B clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
COPY --from=build /app/target/chatbi-copilot.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
