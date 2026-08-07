# ---- Build stage ----
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-17
FROM ${MAVEN_IMAGE} AS build
WORKDIR /app
COPY pom.xml settings.xml ./
COPY src ./src
RUN mvn -s settings.xml -B package -Dmaven.test.skip=true

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
ENV SPRING_PROFILES_ACTIVE=docker
COPY --from=build /app/target/chatbi-copilot.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
