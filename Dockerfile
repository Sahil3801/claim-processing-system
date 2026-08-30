FROM maven:3.9.16-eclipse-temurin-17-alpine AS build

WORKDIR /workspace

# Cache dependency resolution separately from application compilation.
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-alpine AS runtime

LABEL org.opencontainers.image.title="Claims Processing API" \
      org.opencontainers.image.description="Spring Boot claims processing backend"

RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

COPY --from=build --chown=spring:spring /workspace/target/demo-0.0.1-SNAPSHOT.jar ./app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
