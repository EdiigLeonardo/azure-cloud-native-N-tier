# Backend multi-stage Dockerfile (build with mvnw, run on Eclipse Temurin slim)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# copy maven wrapper and project files
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw

# copy source and build
COPY src ./src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY --from=build /workspace/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
