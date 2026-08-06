# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Kopieer pom.xml eerst zodat Maven dependencies gecached worden
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Kopieer broncode en bouw de jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Alpine heeft een klein Tesseract pakket — perfect voor onze use case
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Installeer Tesseract + taaldata (Engels en Nederlands)
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-eng tesseract-ocr-data-nld

# Maak upload directory aan
RUN mkdir -p /tmp/book-scanner-uploads

# Kopieer de gebouwde jar vanuit de build stage
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
