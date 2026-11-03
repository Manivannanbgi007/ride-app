# syntax=docker/dockerfile:1
# Build context: the repository root (docker-compose.yml sets it), because this service
# needs the shared event-contracts library, which is built and installed first.

# ---- Stage 1: build the jar (tests and code quality checks run separately: mvnw verify) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY event-contracts/pom.xml event-contracts/pom.xml
COPY event-contracts/src event-contracts/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f event-contracts/pom.xml install -Dcheckstyle.skip -Dspotbugs.skip
COPY chat-service/pom.xml chat-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f chat-service/pom.xml dependency:go-offline
COPY chat-service/src chat-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f chat-service/pom.xml package -DskipTests

# ---- Stage 2: split the jar into layers so dependency layers are cached ----
FROM eclipse-temurin:21-jre AS layers
WORKDIR /layers
COPY --from=build /workspace/chat-service/target/*.jar application.jar
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
EXPOSE 8096
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]