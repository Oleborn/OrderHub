# OrderHub

**Проект, эволюционирующий из монолита в production-ready микросервисную систему.**

OrderHub — сквозной образовательный проект, который на протяжении 20 практических видео проходит путь от простого Spring Boot приложения до распределённой event-driven системы с Saga, Outbox, идемпотентностью, кэшированием, CQRS и Reactive Streams.

Ключевая идея проекта: **каждое новое архитектурное решение появляется не потому, что «так модно», а потому что предыдущая версия системы столкнулась с конкретной инженерной проблемой** — растущей нагрузкой, потерей данных, рассинхронизацией сервисов или невозможностью обработать пиковый трафик в рамках SLA. Код в репозитории — это результат серии таких решений, а не заранее спроектированная «идеальная» архитектура.

Видео-серия: плейлист **OrderHub** на канале [Java_Beginner](https://www.youtube.com/@Java_Beginner-Dev).

---

## Содержание

- [Архитектура системы](#архитектура-системы)
- [Сервисы](#сервисы)
- [Инфраструктура](#инфраструктура)
- [Эволюция проекта по видео](#эволюция-проекта-по-видео)
- [Быстрый старт](#быстрый-старт)
- [Наблюдаемость](#наблюдаемость)
- [Структура репозитория](#структура-репозитория)
- [Известные особенности и ограничения](#известные-особенности-и-ограничения)

---

## Архитектура системы

```
                          ┌──────────────┐
                POST      │              │
   Клиент ───────────────▶│ order-service│──────┐
                          │  (порт 8081) │      │ Outbox + Debezium (CDC)
                          └──────────────┘      ▼
                                 ▲          ┌─────────┐
                                 │          │  Kafka  │
                     UpdateOrderStatus      └────┬────┘
                     CancelOrderCommand           │
                                 │       order.outbox / payment-events / ...
                          ┌──────┴───────┐         │
                          │  bpm-service │◀────────┤
                          │  (Camunda)   │         │
                          └──────┬───────┘         │
                     ProcessPaymentCommand          │
                                 ▼                 │
                          ┌──────────────┐         │
                          │payment-service│────────┤
                          │  (порт 8083) │         │
                          └──────────────┘         │
                                                    ▼
                     ┌──────────────────┐   ┌──────────────────────────┐
                     │ notification-svc │   │   analytics-service      │
                     │ (блокирующий,    │   │   (CQRS Read Model,      │
                     │  порт 8082)      │   │    порт 8090)            │
                     └────────┬─────────┘   └──────────────────────────┘
                              │
                     ┌────────▼─────────────────┐
                     │ reactive-notification-svc │
                     │ (Reactor Kafka + R2DBC,   │
                     │  порт 8199)               │
                     └────────┬──────────────────┘
                              ▼
                     ┌──────────────────┐
                     │ provider-service │  (мок внешнего email/SMS-провайдера,
                     │  (порт 8198)     │   имитирует rate limit и случайные сбои)
                     └──────────────────┘
```

Все сервисы, кроме `provider-service`, взаимодействуют друг с другом асинхронно через Kafka. Синхронные HTTP-вызовы между сервисами в системе отсутствуют — это осознанное решение, принятое в процессе перехода от Saga Choreography к Saga Orchestration (см. видео 15–16).

---

## Сервисы

| Сервис | Порт | Роль | Технологии |
|---|---|---|---|
| **order-service** | 8081 | Приём заказов, источник истины по заказам, Transactional Outbox | Spring Boot, PostgreSQL, JPA, Redis (кэш + идемпотентность), Resilience4j |
| **payment-service** | 8083 | Обработка платежей как участник Saga | Spring Boot, PostgreSQL, JPA, Kafka |
| **bpm-service** | 8080* | Оркестрация бизнес-процесса заказа через BPMN | Camunda BPM, Spring Boot |
| **notification-service** | 8082 | Отправка уведомлений (блокирующая реализация) | Spring Boot, RestTemplate, Kafka |
| **reactive-notification-service** | 8199 | Отправка уведомлений (неблокирующая реализация) | Reactor Kafka, R2DBC, WebClient, Resilience4j RateLimiter |
| **analytics-service** | 8090 | CQRS Read Model — агрегирует жизненный цикл заказа из событий трёх сервисов | Spring Boot, PostgreSQL, Kafka Consumer |
| **provider-service** | 8198 | Мок внешнего email/SMS-провайдера с искусственным rate limit и случайными сбоями | Spring WebFlux |

\* Порт Camunda задаётся стандартной конфигурацией Spring Boot (обычно 8080); проверьте `docker-compose.yaml` / `BPM_SERVICE_PORT` в вашем `.env` для точного значения при локальном запуске.

**Важно:** `order-service`, `notification-service`, `payment-service`, `bpm-service`, `analytics-service` и `reactive-notification-service` собираются как модули корневого `pom.xml`. `provider-service` — самостоятельный Maven-модуль с собственным родителем (`OrderHub_Project`), но **не включён в список `<modules>` корневого pom** — если вы работаете с проектом в IDE, добавьте его как отдельный Maven-проект или зарегистрируйте модуль вручную, иначе он не соберётся вместе с остальными через `mvn clean install` из корня.

### notification-service vs reactive-notification-service

В репозитории сознательно оставлены **обе** реализации сервиса уведомлений:

- `notification-service` — блокирующая версия (`@KafkaListener` + `RestTemplate` + JDBC). Используется как «контрольная точка» — baseline, с которым сравнивается производительность реактивной версии.
- `reactive-notification-service` — неблокирующая версия (Reactor Kafka + WebClient + R2DBC), которая явно ограничивает concurrency и rate вызовов к провайдеру, чтобы контролируемо работать в условиях жёсткого лимита внешнего API.

Обе версии слушают один и тот же топик `order.outbox` и могут быть запущены и сравнены одновременно — именно так это сделано в финальном (20-м) видео серии, посвящённом нагрузочному тестированию и сравнению подходов.

---

## Инфраструктура

Полный стек поднимается через `docker-compose.yaml` одной командой и включает:

| Компонент | Назначение |
|---|---|
| **PostgreSQL** | Отдельная база на каждый сервис (database-per-service) |
| **Kafka** | Основная шина событий между сервисами |
| **Kafka Connect + Debezium** (`connect`) | Change Data Capture для Transactional Outbox (`connector-config.json`) |
| **Kafka UI** | Веб-интерфейс для просмотра топиков, consumer groups, lag |
| **Redis** | Идемпотентность (Inbox pattern) и распределённый кэш заказов |
| **Prometheus** | Сбор метрик со всех сервисов (`/actuator/prometheus`) |
| **Grafana** | Дашборды — бизнес-метрики, JVM, Kafka consumer lag, latency |
| **Jaeger** | Распределённая трассировка (OpenTelemetry, W3C Trace Context) |
| **ELK-стек** (Elasticsearch, Logstash, Kibana, Filebeat) | Централизованное структурированное логирование |
| **Camunda Web-приложение** (внутри `bpm-service`) | Cockpit для мониторинга BPMN-процессов |

В папке [`guides/`](./guides) собраны развёрнутые конспекты по каждому инфраструктурному компоненту — от базовых концепций до production-эксплуатации. Это не просто заметки «как настроить», а полноценные мини-учебники, использовавшиеся при подготовке соответствующих видео.

| Гайд | Что внутри |
|---|---|
| [`Debezium_guide.md`](./guides/Debezium_guide.md) | CDC, архитектура Debezium, коннекторы, snapshotting, трансформации (SMT), Schema Registry, Java-воркшоп с Embedded Engine |
| [`RabbitMQ_Guide.md`](./guides/RabbitMQ_Guide.md) | Архитектура и философия RabbitMQ, установка, production-конфигурация, Spring AMQP / RabbitTemplate, надёжность и отказоустойчивость |
| [`Camunda-guide.md`](./guides/Camunda-guide.md) | От основ BPMN до Camunda 8: Process Engine, разработка процессов, Enterprise-функции, production-эксплуатация |
| [`Micrometer_guide.md`](./guides/Micrometer_guide.md) | Архитектура Micrometer, типы метрик (Meters), теги и многомерность, интеграция со Spring Boot и Prometheus |
| [`Prometheus_guide.md`](./guides/Prometheus_guide.md) | Архитектура Prometheus, TSDB, PromQL, Service Discovery, Relabeling, Recording/Alerting Rules, масштабирование |
| [`Grafana_guide.md`](./guides/Grafana_guide.md) | Архитектура Grafana, Data Sources, панели и дашборды, Unified Alerting, Provisioning (IaC), безопасность |
| [`OpenTelemetry_guide.md`](./guides/OpenTelemetry_guide.md) | Архитектура OTel, Collector, протокол OTLP, Semantic Conventions, деплой в Kubernetes, интеграция со Spring Boot 3.x |
| [`Jaeger_guide.md`](./guides/Jaeger_guide.md) | Архитектура Jaeger, стратегии сэмплирования, связка с OpenTelemetry, анализ трасс, тюнинг производительности |
| [`ElasticSearch_guide.md`](./guides/ElasticSearch_guide.md) | Внутреннее устройство Lucene, маппинги и типы полей, поиск и анализ, Index Lifecycle Management, безопасность |
| [`Logstash_guide.md`](./guides/Logstash_guide.md) | Архитектура Logstash, написание конфигураций, плагины фильтрации, оптимизация производительности |
| [`Kibana_guide.md`](./guides/Kibana_guide.md) | Discover, Visualize, Dashboards, Kibana Lens, алертинг, безопасность и пространства (Spaces) |

Дополнительно в папке лежат готовые конфигурационные файлы-примеры, на которые ссылаются гайды:

| Файл | Назначение |
|---|---|
| [`resilience4j-config.yaml`](./guides/resilience4j-config.yaml) | Пример конфигурации Retry / CircuitBreaker / Bulkhead / RateLimiter (видео 9–11) |
| [`redis-config-guide.yaml`](./guides/redis-config-guide.yaml) | Пример конфигурации Redis для кэша и идемпотентности (видео 17–18) |
| [`camunda-config-guide.yaml`](./guides/camunda-config-guide.yaml) | Пример конфигурации Camunda BPM (видео 16) |
| [`logstash-guide.conf`](./guides/logstash-guide.conf) | Пример pipeline-конфигурации Logstash (видео 8) |
| [`OptionsRabbitMQ.yaml`](./guides/OptionsRabbitMQ.yaml) | Опции конфигурации RabbitMQ (видео 12) |
| [`rabbitMQ_3Brokers_docker-compose.yml`](./guides/rabbitMQ_3Brokers_docker-compose.yml) | Docker Compose для кластера из 3 брокеров RabbitMQ |

---

## Эволюция проекта по видео

Ниже — сопоставление номеров видео из плейлиста с тем, что было добавлено в код на каждом этапе (по данным истории коммитов).

| № | Тема видео | Что добавлено в код |
|---|---|---|
| 1 | Старт серии. Основа production-системы | Инициализация `order-service`, базовая доменная модель, REST API |
| 2 | Миграции с Flyway и Liquibase | Liquibase-миграции для схемы БД |
| 3 | Data Access Layer: JDBC / JPA / jOOQ | Выбор JPA как основного ORM |
| 4 | Spring Boot Actuator | Базовая observability через Actuator |
| 5 | Бизнес-метрики через AOP | `@BusinessMetric`, `BusinessMetricsAspect`, Micrometer |
| 6 | Prometheus и Grafana | Экспорт метрик, дашборды |
| 7 | Распределённая трассировка: OpenTelemetry & Jaeger | Трейсинг между сервисами |
| 8 | Логирование и ELK-стек | Структурированные JSON-логи, Logstash, Kibana, Filebeat |
| 9 | Retry vs Circuit Breaker | Resilience4j: Retry, CircuitBreaker |
| 10 | Timeout и Bulkhead | Resilience4j: Timeout, Bulkhead |
| 11 | Fallback и graceful degradation | Fallback-методы, деградация функциональности |
| 12 | RabbitMQ: Confirm, DLQ | Асинхронная отправка уведомлений через RabbitMQ (позже заменено на Kafka) |
| 13 | Kafka: от очереди к журналу событий | Полный переход с RabbitMQ на Kafka |
| 14 | Transactional Outbox | Outbox-таблица, Debezium CDC, `DebeziumConnectorInitializer` |
| 15 | Saga Choreography | `payment-service`, событийный обмен `order-service` ↔ `payment-service` |
| 16 | Saga Orchestration с Camunda | `bpm-service`, BPMN-процесс (`orderhub-bpm.bpmn`), делегаты Camunda |
| 17 | Идемпотентность в микросервисах | `IdempotencyService`, `ProcessedCommand`, Redis-дедупликация |
| 18 | Кэширование в микросервисах | `OrderCacheMapService` → `CaffeineOrderCacheService` → `OrderCacheService` (Redis), CircuitBreaker-фолбэк |
| 19 | Read Model на событиях: CQRS | `analytics-service`, `OrderLifecycle`, consumer'ы трёх событий, REST Query API |
| 20 (финал) | Reactive: R2DBC + Kafka + управление потоком данных | `provider-service` (мок с rate limit), `reactive-notification-service` (Reactor Kafka, WebClient, R2DBC, RateLimiter) |

История версий кэш-сервиса в `order-service` (`OrderCacheMapService` → `CaffeineOrderCacheService` → `OrderCacheService`) сохранена в кодовой базе намеренно — все три класса присутствуют одновременно, отражая последовательные шаги эволюции от `HashMap` к Caffeine и затем к распределённому кэшу на Redis, разобранные в видео 18.

---

## Быстрый старт

### Требования

- Docker и Docker Compose
- Java 21
- Maven 3.9+
- ~8 ГБ свободной оперативной памяти для полного стека (Kafka, Postgres, ELK, Camunda и т.д. одновременно)

### Переменные окружения

Проект ожидает `.env`-файл в корне (не включён в репозиторий) со следующими переменными:

```env
# PostgreSQL
POSTGRES_DB=orderhub
POSTGRES_USER=orderhub_user
POSTGRES_PASSWORD=change_me
POSTGRES_PORT=5432

# Порты сервисов
ORDER_SERVICE_PORT=8081
NOTIFICATION_SERVICE_PORT=8082
PAYMENT_SERVICE_PORT=8083
BPM_SERVICE_PORT=8080
ANALITICS_SERVICE_PORT=8090
REACTIVE_NOTIFICATION_SERVICE_PORT=8199
PROVIDER_SERVICE_PORT=8198

# Межсервисные URL (для BPM-делегатов и т.д.)
BPM_SERVICE_URL=http://bpm-service:8080
NOTIFICATION_SERVICE_URL=http://notification-service:8082
PAYMENT_SERVICE_URL=http://payment-service:8083

# ELK
ELASTICSEARCH_HOST=elasticsearch
ELASTICSEARCH_PORT=9200
KIBANA_PORT=5601
LOGSTASH_PORT=5044

# Grafana
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=admin

# Camunda
CAMUNDA_ADMIN_ID=demo
CAMUNDA_ADMIN_PASSWORD=demo

# Прочее
APP_VERSION=0.0.1
CLUSTER_ID=<сгенерированный Kafka KRaft cluster id>
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

### Запуск

```bash
# 1. Собрать все модули (из корня — provider-service соберите отдельно, см. примечание выше)
mvn clean package -DskipTests

# 2. Поднять весь стек
docker compose up -d --build

# 3. Проверить состояние сервисов
docker compose ps
```

После старта:

- **Kafka UI** — `http://localhost:<KAFKA_UI_PORT>` — просмотр топиков и consumer lag
- **Grafana** — `http://localhost:3000` (или порт из `.env`) — дашборды
- **Jaeger UI** — `http://localhost:16686` — трейсы запросов
- **Kibana** — `http://localhost:<KIBANA_PORT>` — логи
- **Camunda Cockpit** — `http://localhost:<BPM_SERVICE_PORT>` — мониторинг BPMN-процессов

### Создание тестового заказа

```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "test-customer",
    "items": [{"productId": "sku-1", "quantity": 1}]
  }'
```

Дальше заказ проходит по цепочке: `order-service` → Outbox → Kafka → `bpm-service` (оркестрация) → `payment-service` → уведомление через `notification-service` или `reactive-notification-service` → фиксация в `analytics-service`.

---

## Наблюдаемость

Каждый сервис экспортирует:

- **Метрики** — через `/actuator/prometheus` (бизнес-метрики через `@BusinessMetric`, JVM-метрики, Kafka consumer lag)
- **Трейсы** — через OTLP в Jaeger (`management.otlp.tracing.endpoint`), с W3C Trace Context propagation
- **Структурированные логи** — в JSON через `logstash-logback-encoder`, собираются Filebeat → Logstash → Elasticsearch

Готовые Grafana-дашборды и Prometheus-конфигурация лежат в соответствующих директориях, подключаемых через `docker-compose.yaml`.

---

## Структура репозитория

```
OrderHub/
├── order-service/                   # Приём заказов, Outbox, кэш, идемпотентность
├── payment-service/                 # Обработка платежей (Saga participant)
├── bpm-service/                     # Camunda-оркестрация (Saga Orchestration)
├── notification-service/            # Уведомления, блокирующая реализация
├── reactive-notification-service/   # Уведомления, реактивная реализация (Reactor Kafka + R2DBC)
├── analytics-service/               # CQRS Read Model (жизненный цикл заказа)
├── provider-service/                # Мок внешнего провайдера уведомлений
├── guides/                          # Конспекты по настройке инфраструктурных компонентов
├── logstash/                        # Конфигурация Logstash pipeline
├── filebeat.yml                     # Конфигурация Filebeat
├── connector-config.json            # Конфигурация Debezium-коннектора для Kafka Connect
├── docker-compose.yaml              # Полный инфраструктурный стек
└── pom.xml                          # Родительский Maven POM (Spring Boot 3.5.10, Java 21)
```

---

## Известные особенности и ограничения

Проект — учебный, и часть решений в нём осознанно упрощена или помечена как компромисс, разобранный в соответствующем видео:

- **`provider-service` не зарегистрирован как модуль** в корневом `pom.xml` — соберите его отдельно при локальной разработке.
- **`notification-service` и `reactive-notification-service` работают параллельно** и оба слушают `order.outbox` — это намеренное дублирование для сравнения производительности, а не архитектурная ошибка.
- Некоторые сервисы (`bpm-service`, `analytics-service`, `payment-service`) используют упрощённый `<description>` в `pom.xml`, совпадающий с `artifactId`, — это не влияет на сборку.
- Часть решений (например, upsert без версионирования событий, простая стратегия retry) сознательно оставлена в демонстрационном виде — соответствующие видео явно проговаривают, что для реального production потребовалось бы дополнительное усиление (сравнение timestamp'ов при конкурентных обновлениях, распределённый rate limiter при нескольких инстансах и т.д.).
- Тестовое покрытие, security и CI/CD **не входят** в этот репозиторий — они разобраны в отдельных плейлистах на канале и не дублируются здесь, чтобы не размывать фокус проекта.

---

## Лицензия и авторство

Проект создан в образовательных целях для сопровождения видео-серии на YouTube-канале [Java_Beginner](https://www.youtube.com/@Java_Beginner-Dev). Код открыт для изучения, форков и использования в собственных learning-проектах.

Ваш [@Oleborn](https://t.me/Oleborn)
