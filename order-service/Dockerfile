# =============================================================================
# DOCKERFILE ДЛЯ ORDERHUB - PRODUCTION BUILD
# =============================================================================
# Многоступенчатая сборка (multi-stage build):
#   Этап 1 (builder): собирает приложение с Maven и JDK
#   Этап 2 (final): только JRE и собранный jar
#
# Преимущества:
#   - Финальный образ в 5-10 раз меньше (не содержит Maven, JDK, исходников)
#       (сначала все используем для сборки но в основной образ не кладем)
#   - Безопаснее (нет инструментов сборки в production)
#   - Быстрее деплой (меньше размер)
#   - Кэширование зависимостей (docker layer caching)
# =============================================================================

# -----------------------------------------------------------------------------
# ЭТАП 1: СБОРКА (BUILDER)
# -----------------------------------------------------------------------------
# Используем полный образ с Maven и JDK 21
# eclipse-temurin — официальный OpenJDK build от Adoptium
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Аргументы сборки (передаются из CI/CD, например: --build-arg APP_VERSION=1.2.3)
# НЕ ИСПОЛЬЗУЙТЕ для секретов (паролей, токенов) — они останутся в образе!
ARG APP_VERSION
ARG BUILD_DATE

# LABEL добавляет метаданные к образу
# docker inspect покажет версию и дату сборки
LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL maintainer="OrderHub Team"

# Рабочая директория внутри контейнера
WORKDIR /app

# Копируем POM-файл и настройки Maven
# Важно: сначала копируем только pom.xml, потом исходники
# Это позволяет закэшировать слой с зависимостями
COPY pom.xml ./pom.xml
COPY order-service/pom.xml ./order-service/pom.xml

# Переходим в папку модуля
WORKDIR /app/order-service

# Загружаем все зависимости без сборки
# --mount=type=cache сохраняет кэш Maven между сборками (ускоряет локальные сборки)
# -B (batch mode) — отключает интерактивный режим
# dependency:go-offline — скачивает всё, что нужно для сборки
RUN --mount=type=cache,target=/root/.m2 \
    mvn dependency:go-offline -B \
    -Dmaven.wagon.http.retryHandler.count=3

# Теперь копируем исходный код
# Этот слой инвалидируется при каждом изменении кода
COPY order-service/src ./src

# Собираем приложение
# clean — удаляем предыдущие артефакты
# package — создаём jar
# -DskipTests — не запускаем тесты (тесты должны быть запущены до сборки образа)
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -DskipTests -B

# -----------------------------------------------------------------------------
# ЭТАП 2: ФИНАЛЬНЫЙ ОБРАЗ (PRODUCTION)
# -----------------------------------------------------------------------------
# Используем минимальный образ с JRE (без JDK, без компилятора)
# alpine — максимально лёгкий Linux (≈5MB)
FROM eclipse-temurin:21-jre-alpine

# Повторяем аргументы для LABEL (они не передаются между этапами автоматически)
ARG APP_VERSION
ARG BUILD_DATE

# Метаданные финального образа
LABEL version=${APP_VERSION}
LABEL build-date=${BUILD_DATE}
LABEL description="OrderHub Production Application"
LABEL org.opencontainers.image.source="https://github.com/Oleborn/OrderHub"

WORKDIR /app

# Устанавливаем curl для healthcheck
# apk add — пакетный менеджер Alpine
# --no-cache — не сохранять индекс пакетов (меньше размер)
RUN apk add --no-cache curl

# Создаём непривилегированного пользователя для безопасности
# spring:spring — пользователь и группа
# -S — системный пользователь (без домашней директории)
RUN addgroup -S spring && adduser -S spring -G spring

# Копируем собранный jar из предыдущего этапа
# --from=builder — берём файл из этапа "builder"
COPY --from=builder /app/order-service/target/*.jar app.jar

# Документируем, какой порт использует приложение
# Не открывает порт автоматически, только информация
EXPOSE 8081

# Переключаемся на непривилегированного пользователя
# ВСЕГДА делайте это в production! Иначе приложение работает от root
USER spring:spring

# Healthcheck для оркестраторов (Kubernetes, Docker Swarm)
# Проверяет /actuator/health каждые 30 секунд
# Если 3 раза подряд неудача — контейнер считается нездоровым
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8081/actuator/health || exit 1

# Точка входа — команда по умолчанию
# Переменные окружения (SPRING_DATASOURCE_URL и др.) подставляются из docker-compose
# Массивная форма ["java", "-jar"] предпочтительнее shell-формы
ENTRYPOINT ["java", "-jar", "app.jar"]