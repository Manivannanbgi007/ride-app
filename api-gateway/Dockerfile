# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar (tests and code quality checks run separately: mvnw verify) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests

# ---- Stage 2: split the jar into layers so dependency layers are cached ----
FROM eclipse-temurin:21-jre AS layers
WORKDIR /layers
COPY --from=build /workspace/target/*.jar application.jar
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
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
