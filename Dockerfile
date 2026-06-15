FROM node:22-bookworm-slim AS frontend-build
WORKDIR /frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /build

COPY pom.xml .
COPY src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Tesseract OCR + idioma espanol.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-spa \
        tesseract-ocr-eng \
        libtesseract-dev \
        libleptonica-dev \
        liblept5 \
        poppler-utils \
        ghostscript \
        curl \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/uploads /app/logs

COPY --from=backend-build /build/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
