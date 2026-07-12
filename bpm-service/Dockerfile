FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Аргументы сборки (передаются из CI/CD)
ARG APP_VERSION
ARG BUILD_DATE

# Метаданные образа сборки
LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL maintainer="OrderHub Team"

WORKDIR /app

# Копируем родительский POM и POM модуля
COPY pom.xml ./pom.xml
COPY bpm-service/pom.xml ./bpm-service/pom.xml

# Переходим в папку модуля
WORKDIR /app/bpm-service

# Загружаем зависимости (кэшируется, если POM не менялся)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# Копируем исходный код модуля
COPY bpm-service/src ./src

# Собираем только bpm-service
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -Dmaven.test.skip=true -B

# -----------------------------------------------------------------------------
# ЭТАП 2: ФИНАЛЬНЫЙ ОБРАЗ (PRODUCTION)
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

# Аргументы для меток
ARG APP_VERSION
ARG BUILD_DATE

# Метаданные финального образа
LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL description="bpm-service for OrderHub"
LABEL org.opencontainers.image.source="https://github.com/Oleborn/OrderHub"

WORKDIR /app

# Устанавливаем curl для healthcheck (опционально)
RUN apk add --no-cache curl

# Создаём непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring

# Копируем jar из этапа сборки
COPY --from=builder /app/bpm-service/target/*.jar app.jar

# Открываем порт
EXPOSE 8089

# Переключаемся на непривилегированного пользователя
USER spring:spring

# Healthcheck (опционально, можно добавить если нужен)
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8089/actuator/health || exit 1

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]