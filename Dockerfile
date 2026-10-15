# Backend (Spring Boot) para despliegue con docker-compose.prod.yml.
# Etapa 1: compila el .jar. Etapa 2: imagen liviana solo con el JRE.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 archivo \
    && mkdir -p /data/documentos \
    && chown -R archivo /data
WORKDIR /app
COPY --from=build /src/target/*.jar /app/app.jar
USER archivo
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
