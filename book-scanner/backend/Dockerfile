# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.7-eclipse-temurin-21 AS build
WORKDIR /app

# Kopieer pom.xml eerst zodat Maven dependencies gecached worden
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kopieer broncode en bouw de jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Maak upload directory aan
RUN mkdir -p /tmp/book-scanner-uploads

# Kopieer de gebouwde jar vanuit de build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
