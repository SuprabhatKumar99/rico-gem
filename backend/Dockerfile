FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S ricozknow && adduser -S ricozknow -G ricozknow
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER ricozknow
EXPOSE 8080 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -q -O - http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
