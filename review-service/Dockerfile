# syntax=docker/dockerfile:1
# Build context: the repository root (docker-compose.yml sets it), like the other services.
# review-service has no Kafka events, so unlike promotion-service it needs no event-contracts build.

# ---- Stage 1: build the jar (tests and code quality checks run separately: mvnw verify) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY review-service/pom.xml review-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f review-service/pom.xml dependency:go-offline
COPY review-service/src review-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f review-service/pom.xml package -DskipTests

# ---- Stage 2: split the jar into layers so dependency layers are cached ----
FROM eclipse-temurin:21-jre AS layers
WORKDIR /layers
COPY --from=build /workspace/review-service/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Stage 3: runtime image, runs as a non-root user ----
FROM eclipse-temurin:21-jre
WORKDIR /application
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
EXPOSE 8094
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
