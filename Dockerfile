# Backend (Spring Boot) para despliegue con docker-compose.prod.yml.
# Etapa 1: compila el .jar. Etapa 2: imagen liviana solo con el JRE.
#
# El caché de Maven (/root/.m2) se conserva entre intentos: si la conexión se
# corta a mitad, al reintentar continúa con lo que ya descargó.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -Dmaven.wagon.http.retryHandler.count=10 \
        -Dmaven.wagon.http.retryHandler.requestSentEnabled=true \
        dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -B -Dmaven.wagon.http.retryHandler.count=10 -DskipTests package

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
