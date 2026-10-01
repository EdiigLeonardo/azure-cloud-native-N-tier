# Multi-stage: build with Maven, run on a small JRE image
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/target/riskcube-*.jar app.jar
USER 1000
EXPOSE 8080
# In a container, size the heap from the container memory limit (not the host RAM).
# 75% heap leaves room for metaspace, thread stacks, direct/off-heap buffers.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp", "-jar", "app.jar"]
