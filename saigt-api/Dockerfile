# Build stage — base image já tem mvn, não copiar mvnw
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
# gdalinfo inspeciona os GeoTIFF carregados (versao fixa do repositorio community do Alpine 3.24 da imagem base).
RUN apk add --no-cache gdal-tools=3.13.3-r0
RUN addgroup -S conkord && adduser -S conkord -G conkord
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER conkord
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
