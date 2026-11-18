# Builds the jar inside Docker, so running the app needs only Docker (no JDK on the host).
# Used by the `app` service in docker-compose.yml: `docker compose --profile app up --build`.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# The cache mount keeps Maven's downloads between builds, so a rebuild after a code change is quick.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
