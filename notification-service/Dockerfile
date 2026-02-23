# =============================================================================
# DOCKERFILE ДЛЯ NOTIFICATION-SERVICE - PRODUCTION BUILD
# =============================================================================
# Многоступенчатая сборка (multi-stage build):
#   Этап 1 (builder): собирает приложение с Maven и JDK
#   Этап 2 (final): только JRE и собранный jar
#
# Преимущества:
#   - Финальный образ в 5-10 раз меньше
#   - Безопаснее (нет инструментов сборки в production)
#   - Быстрее деплой (меньше размер)
# =============================================================================

# -----------------------------------------------------------------------------
# ЭТАП 1: СБОРКА (BUILDER)
# -----------------------------------------------------------------------------
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
COPY notification-service/pom.xml ./notification-service/pom.xml

# Переходим в папку модуля
WORKDIR /app/notification-service

# Загружаем зависимости (кэшируется, если POM не менялся)
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B

# Копируем исходный код модуля
COPY notification-service/src ./src

# Собираем только notification-service
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
LABEL description="Notification Service for OrderHub"
LABEL org.opencontainers.image.source="https://github.com/Oleborn/OrderHub"

WORKDIR /app

# Устанавливаем curl для healthcheck (опционально)
RUN apk add --no-cache curl

# Создаём непривилегированного пользователя
RUN addgroup -S spring && adduser -S spring -G spring

# Копируем jar из этапа сборки
COPY --from=builder /app/notification-service/target/*.jar app.jar

# Открываем порт
EXPOSE 8082

# Переключаемся на непривилегированного пользователя
USER spring:spring

# Healthcheck (опционально, можно добавить если нужен)
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# Запускаем приложение
ENTRYPOINT ["java", "-jar", "app.jar"]