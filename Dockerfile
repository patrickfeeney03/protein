FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
COPY config/ config/

RUN ./mvnw -B -DskipTests package

FROM mcr.microsoft.com/playwright/java:v1.59.0-noble

WORKDIR /app

COPY --from=build --chown=pwuser:pwuser /app/target/*.jar /app/app.jar

EXPOSE 8080

USER pwuser

CMD ["sh", "-c", "java -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod} -Dserver.port=${PORT:-8080} ${JAVA_OPTS} -jar /app/app.jar"]
