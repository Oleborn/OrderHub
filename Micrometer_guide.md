# Micrometer Deep Dive: Наблюдаемость бизнес-процессов в Spring Boot

Это исчерпывающее руководство по **Micrometer** — инструментальному фасаду для сбора метрик в JVM-приложениях, особенно в контексте **Spring Boot**. Мы углубимся в его архитектуру, типы измерителей, продвинутые техники конфигурации и лучшие практики, чтобы превратить ваш сервис из "черного ящика" в полностью наблюдаемую систему.

---

## 1. Введение: От технических метрик к бизнес-показателям

В современном мире микросервисов и облачных вычислений наблюдаемость (Observability) является ключевым фактором успеха. Традиционно, мониторинг фокусировался на **технических метриках**: загрузка CPU, использование памяти, количество HTTP-запросов, ошибки 5xx. Эти данные, предоставляемые такими инструментами, как Spring Boot Actuator, безусловно, важны для понимания здоровья инфраструктуры.

Однако, существует фундаментальный разрыв между **техническим здоровьем** и **бизнес-здоровьем** приложения. Представьте ситуацию: ваш сервис по обработке заказов работает, CPU и память в норме, все HTTP-запросы возвращают 200 OK. Но по какой-то причине, связанной с бизнес-логикой или внешним сервисом, заказы перестали создаваться. Технические метрики будут показывать "зеленый" статус, в то время как бизнес будет нести убытки.

**Micrometer** призван устранить этот разрыв. Он позволяет разработчикам внедрять **бизнес-метрики** непосредственно в код, делая приложение "говорящим" на языке бизнеса. Это не просто добавление библиотеки, это изменение парадигмы: от "я жив" к "я выполняю свою функцию и делаю это хорошо".

---

## 2. Архитектура Micrometer: Фасад для наблюдаемости

Micrometer позиционируется как **"SLF4J для метрик"**. Это означает, что он предоставляет единый API для инструментирования вашего кода, абстрагируя его от конкретной системы мониторинга (Prometheus, Datadog, Graphite, InfluxDB, New Relic и т.д.). Вы пишете код один раз, а выбор бэкенда для хранения и визуализации метрик остается за конфигурацией.

### Ключевые компоненты:

1.  **`MeterRegistry`**: Центральный компонент Micrometer. Это интерфейс, который управляет жизненным циклом всех измерителей (Meters). Каждая система мониторинга имеет свою реализацию `MeterRegistry` (например, `PrometheusMeterRegistry`, `DatadogMeterRegistry`). Spring Boot автоматически конфигурирует подходящий `MeterRegistry` на основе добавленных зависимостей.

2.  **`Meter`**: Базовый интерфейс для всех типов измерителей. Он представляет собой абстракцию для сбора данных. Micrometer предоставляет несколько конкретных реализаций `Meter`:
    *   `Counter`
    *   `Timer`
    *   `Gauge`
    *   `DistributionSummary`
    *   `LongTaskTimer`

3.  **`Meter.Id`**: Уникальный идентификатор для каждого измерителя, состоящий из имени (name) и набора тегов (tags). Теги являются ключевой особенностью Micrometer, позволяющей добавлять многомерность к метрикам.

4.  **`Tag`**: Пара ключ-значение, которая добавляет контекст к метрике. Например, метрика `orders.created.total` может иметь тег `status="success"` или `status="error"`. Это позволяет агрегировать и фильтровать данные в системах мониторинга.

5.  **`MeterFilter`**: Мощный механизм для управления метриками до их регистрации в `MeterRegistry`. Фильтры позволяют:
    *   **Отклонять (Deny) или принимать (Accept)** метрики на основе их имени или тегов.
    *   **Трансформировать (Transform)** `Meter.Id`, изменяя имя или добавляя/удаляя теги.
    *   **Конфигурировать (Configure)** статистику распределения для `Timer` и `DistributionSummary` (например, задавать перцентили или SLO).

6.  **`MeterBinder`**: Интерфейс для компонентов, которые регистрируют группу связанных метрик. Spring Boot Actuator использует `MeterBinder` для автоматической регистрации стандартных метрик JVM, HTTP-сервера и т.д.

7.  **`Observation API` (Spring Boot 3.x)**: Более современный и унифицированный подход к наблюдаемости, который объединяет метрики, трассировку (tracing) и логирование. `Observation API` позволяет инструментировать бизнес-операции, автоматически генерируя соответствующие метрики и спаны трассировки. Это эволюция Micrometer, предоставляющая более целостный взгляд на выполнение операций.

---

## 3. Типы измерителей (Meters) в деталях

Micrometer предлагает богатый набор примитивов для сбора различных типов данных. Понимание их назначения критически важно для эффективного инструментирования.

### 3.1. Counter (Счетчик)

**Назначение**: Отслеживает кумулятивное количество событий. Счетчик может только увеличиваться.

**Использование**: Идеален для подсчета:
*   Количества выполненных операций (например, `orders.created.total`).
*   Количества ошибок (`orders.failed.total`).
*   Количества входящих запросов.

**Пример кода (Java)**:
```java
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

// ... в вашем сервисе или компоненте

private final Counter orderCreatedCounter;
private final Counter orderFailedCounter;

public MyService(MeterRegistry registry) {
    this.orderCreatedCounter = Counter.builder("orders.created.total")
            .description("Общее количество созданных заказов")
            .tag("status", "success") // Пример тега
            .register(registry);

    this.orderFailedCounter = Counter.builder("orders.created.total") // То же имя, но другой тег
            .description("Общее количество ошибок при создании заказов")
            .tag("status", "failed")
            .register(registry);
}

public void processOrder() {
    try {
        // Логика создания заказа
        orderCreatedCounter.increment(); // Увеличить на 1
    } catch (Exception e) {
        orderFailedCounter.increment(1.0); // Увеличить на заданное значение
    }
}
```

**Важно**: Используйте теги для добавления контекста, а не создавайте отдельные метрики для каждого варианта (например, `orders.created.success` и `orders.created.failed`). Одна метрика с тегом `status` гораздо гибче.

### 3.2. Timer (Таймер)

**Назначение**: Измеряет длительность событий и их распределение. Таймеры предоставляют не только общее время и количество вызовов, но и такие важные статистики, как среднее, максимальное время, а также перцентили (p50, p95, p99).

**Использование**: Идеален для измерения:
*   Времени выполнения критически важных бизнес-операций (например, `orders.creation.duration`).
*   Времени ответа внешних API.
*   Времени запросов к базе данных.

**Перцентили vs. Среднее**: Среднее время выполнения может быть обманчивым. Если 99% запросов выполняются за 100 мс, а 1% — за 5 секунд, среднее будет выглядеть приемлемо. Перцентили (например, p99) показывают, что 99% запросов выполняются быстрее определенного значения, что дает гораздо более точное представление о пользовательском опыте.

**Пример кода (Java)**:
```java
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import io.micrometer.core.annotation.Timed; // Для аннотации

// ...

private final Timer orderCreationTimer;

public MyService(MeterRegistry registry) {
    this.orderCreationTimer = Timer.builder("orders.creation.duration")
            .description("Время, затраченное на создание заказа")
            .publishPercentiles(0.5, 0.95, 0.99) // Публиковать 50-й, 95-й и 99-й перцентили
            .register(registry);
}

// Вариант 1: Ручное инструментирование
public Order createOrderManual() {
    return orderCreationTimer.record(() -> {
        // Логика создания заказа
        return new Order();
    });
}

// Вариант 2: Использование аннотации @Timed (требует AOP)
@Timed(value = "orders.creation.duration.annotated", description = "Время создания заказа через аннотацию")
public Order createOrderAnnotated() {
    // Логика создания заказа
    return new Order();
}
```

### 3.3. Gauge (Измеритель)

**Назначение**: Отслеживает текущее значение. В отличие от счетчика, измеритель может как увеличиваться, так и уменьшаться.

**Использование**: Идеален для мониторинга:
*   Размера коллекций (например, количество элементов в очереди, размер кэша).
*   Текущего количества активных соединений.
*   Значений, которые могут колебаться (например, температура, уровень топлива).

**Пример кода (Java)**:
```java
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// ...

private final Queue<Order> processingQueue = new ConcurrentLinkedQueue<>();

public MyService(MeterRegistry registry) {
    // Вариант 1: Измерение размера коллекции
    Gauge.builder("orders.queue.size", processingQueue, Queue::size)
            .description("Текущее количество заказов в очереди")
            .register(registry);

    // Вариант 2: Измерение произвольного значения через Supplier
    Gauge.builder("system.temperature", () -> getSystemTemperature())
            .description("Текущая температура системы")
            .tag("unit", "celsius")
            .register(registry);
}

private double getSystemTemperature() {
    // ... логика получения температуры
    return 25.5;
}

public void addToQueue(Order order) {
    processingQueue.offer(order);
}

public Order processFromQueue() {
    return processingQueue.poll();
}
```

### 3.4. DistributionSummary (Сводка распределения)

**Назначение**: Измеряет распределение произвольных значений (не времени). Подобно `Timer`, предоставляет count, total, max и перцентили.

**Использование**: Подходит для измерения:
*   Размера полезной нагрузки запроса/ответа (`request.payload.size`).
*   Количества элементов в заказе (`order.items.count`).
*   Размера файла.

**Пример кода (Java)**:
```java
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;

// ...

private final DistributionSummary orderItemCountSummary;

public MyService(MeterRegistry registry) {
    this.orderItemCountSummary = DistributionSummary.builder("order.items.count")
            .description("Количество позиций в заказе")
            .baseUnit("items")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry);
}

public void createOrder(List<OrderItem> items) {
    // ... логика
    orderItemCountSummary.record(items.size());
}
```

### 3.5. LongTaskTimer (Таймер длительных задач)

**Назначение**: Измеряет количество и длительность *одновременно выполняющихся* задач. В отличие от обычного `Timer`, который измеряет завершенные задачи, `LongTaskTimer` полезен для мониторинга асинхронных или долгоживущих операций.

**Использование**: Подходит для:
*   Мониторинга длительных фоновых задач.
*   Отслеживания времени выполнения операций, которые могут быть прерваны.
*   Оценки задержек в очередях обработки.

**Пример кода (Java)**:
```java
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;

// ...

private final LongTaskTimer longRunningProcessTimer;

public MyService(MeterRegistry registry) {
    this.longRunningProcessTimer = LongTaskTimer.builder("long.running.process")
            .description("Длительность текущих долгих задач")
            .register(registry);
}

public void startLongProcess() {
    LongTaskTimer.Sample sample = longRunningProcessTimer.start();
    try {
        // ... долгая асинхронная логика
    } finally {
        sample.stop();
    }
}

// Или с аннотацией
@Timed(value = "long.running.process.annotated", longTask = true)
public void startLongProcessAnnotated() {
    // ... долгая асинхронная логика
}
```

---

## 4. Теги (Tags) и многомерность метрик

Теги — это сердце многомерной модели данных Micrometer. Они позволяют добавлять произвольные пары ключ-значение к каждой метрике, что дает огромную гибкость при запросах и агрегации в системах мониторинга.

### 4.1. Важность тегов

Вместо создания метрик с уникальными именами для каждого варианта (например, `orders.created.europe`, `orders.created.asia`), вы создаете одну метрику `orders.created.total` и добавляете тег `region="europe"` или `region="asia"`. Это позволяет:
*   **Гибкость запросов**: Вы можете легко фильтровать, группировать и агрегировать данные по любому тегу.
*   **Уменьшение количества уникальных имен метрик**: Системы мониторинга лучше справляются с меньшим количеством имен метрик, но с большим количеством тегов.
*   **Единообразие**: Всегда используйте одни и те же теги для связанных метрик.

### 4.2. Глобальные теги

Вы можете определить глобальные теги, которые будут автоматически добавлены ко всем метрикам. Это удобно для таких вещей, как имя приложения, версия, среда развертывания, имя хоста.

**application.yaml**:
```yaml
management:
  metrics:
    tags:
      application: ${spring.application.name}
      environment: ${spring.profiles.active:default}
      host: ${HOSTNAME:unknown}
```

### 4.3. Проблема высокой кардинальности (High Cardinality)

**Высокая кардинальность** возникает, когда тег имеет потенциально неограниченное количество уникальных значений (например, ID пользователя, ID сессии, полный URL с параметрами запроса). Это **очень опасно** для систем мониторинга, так как приводит к:
*   **Взрывному росту временных рядов**: Каждая уникальная комбинация имени метрики и тегов создает новый временной ряд. Миллионы временных рядов могут привести к перегрузке и падению системы мониторинга.
*   **Увеличению затрат**: Многие системы мониторинга тарифицируются по количеству временных рядов.
*   **Снижению производительности**: Запросы к базе данных метрик становятся медленными.

**Как избежать высокой кардинальности**:
*   **Нормализация**: Вместо `user_id=12345` используйте `user_group=premium` или `user_type=guest`.
*   **Шаблонизация URL**: Вместо `/users/12345/profile` используйте `/users/{id}/profile` в теге `uri`.
*   **Используйте `MeterFilter`**: Отклоняйте или трансформируйте теги с высокой кардинальностью (см. раздел 5).

---

## 5. Продвинутая конфигурация: Meter Filters

`MeterFilter` — это мощный механизм для тонкой настройки поведения метрик. Фильтры применяются к `Meter.Id` перед регистрацией измерителя в `MeterRegistry`.

### 5.1. Отклонение (Deny) и Принятие (Accept) метрик

Вы можете контролировать, какие метрики будут зарегистрированы, а какие — нет. Это полезно для уменьшения шума или отключения нежелательных метрик.

**Пример (Java)**:
```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

@Configuration
public class MeterFilterConfig {

    @Bean
    public MeterFilter commonMeterFilter() {
        return MeterFilter.commonTags(Arrays.asList(
            Tag.of("region", "eu-central-1"),
            Tag.of("instance", "my-app-01")
        ));
    }

    @Bean
    public MeterFilter jvmMetricsFilter() {
        // Отклонить все метрики JVM, начинающиеся с "jvm."
        return MeterFilter.denyNameStartsWith("jvm.");
    }

    @Bean
    public MeterFilter httpMetricsFilter() {
        // Принять только HTTP-метрики, остальные отклонить (если нет других ACCEPT-фильтров)
        return MeterFilter.acceptNameStartsWith("http.server.requests");
    }

    @Bean
    public MeterFilter highCardinalityTagFilter() {
        // Удалить тег "username" из всех метрик, чтобы избежать высокой кардинальности
        return MeterFilter.ignoreTags("username");
    }
}
```

**`MeterFilterReply`**: Фильтры возвращают `DENY`, `ACCEPT` или `NEUTRAL`.
*   `DENY`: Метрика не будет зарегистрирована.
*   `ACCEPT`: Метрика будет зарегистрирована немедленно, игнорируя последующие фильтры.
*   `NEUTRAL`: Обработка продолжается следующим фильтром.

### 5.2. Трансформация метрик

`MeterFilter` может изменять `Meter.Id`, например, переименовывать метрики или теги.

**Пример (Java)**:
```java
@Bean
public MeterFilter renameTagFilter() {
    return MeterFilter.renameTag("http.server.requests", "uri", "endpoint");
    // Переименовать тег 'uri' в 'endpoint' для метрик 'http.server.requests'
}

@Bean
public MeterFilter replaceTagValueFilter() {
    return MeterFilter.replaceTagValues("uri", s -> {
        if (s.contains("users")) return "/users/{id}";
        return s;
    });
    // Заменить конкретные значения тега 'uri' на шаблоны для уменьшения кардинальности
}
```

### 5.3. Конфигурация статистики распределения

Для `Timer` и `DistributionSummary` можно настроить параметры распределения (перцентили, гистограммы, SLO).

**Пример (Java)**:
```java
@Bean
public MeterFilter timerConfigFilter() {
    return MeterFilter.all(id -> {
        if (id.getName().startsWith("orders.creation.duration")) {
            return DistributionStatisticConfig.builder()
                    .percentiles(0.5, 0.9, 0.99) // 50-й, 90-й, 99-й перцентили
                    .percentilesHistogram(true) // Включить гистограмму
                    .serviceLevelObjectives(Duration.ofMillis(100), Duration.ofMillis(500)) // SLO
                    .build()
                    .merge(id.getDistributionStatisticConfig());
        }
        return id.getDistributionStatisticConfig();
    });
}
```

---

## 6. Интеграция с Spring Boot

Spring Boot обеспечивает глубокую интеграцию с Micrometer, автоматизируя большую часть конфигурации.

### 6.1. Зависимости

Для базовой функциональности Micrometer достаточно `spring-boot-starter-actuator`. Для экспорта в Prometheus добавьте:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <scope>runtime</scope>
</dependency>
```

### 6.2. Автоматическая конфигурация

Spring Boot автоматически:
*   Создает `MeterRegistry` (например, `PrometheusMeterRegistry`), если найден соответствующий бин.
*   Регистрирует стандартные `MeterBinder`'ы для метрик JVM, Tomcat, DataSource, Logback и т.д.
*   Предоставляет эндпоинт `/actuator/metrics` для просмотра всех метрик и `/actuator/prometheus` для сбора Prometheus.

### 6.3. Настройка через `application.properties`/`application.yaml`

Spring Boot позволяет конфигурировать Micrometer через свойства:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus # Включаем эндпоинт Prometheus
  metrics:
    distribution:
      percentiles:
        http.server.requests: 0.5,0.9,0.99 # Перцентили для HTTP-запросов
      slo:
        http.server.requests: 100ms,500ms # SLO для HTTP-запросов
    tags:
      application: orderhub # Глобальный тег
    enable:
      all: true # Включить все метрики по умолчанию
      jvm: false # Отключить метрики JVM (пример)
```

---

## 7. Интеграция с Prometheus и Grafana

Micrometer генерирует метрики в формате, который легко собирается **Prometheus**. Prometheus — это система мониторинга и оповещения с открытым исходным кодом, которая собирает метрики из сконфигурированных целей по HTTP-протоколу (pull-модель).

### 7.1. Как Prometheus собирает метрики

1.  **Экспозиция**: Spring Boot приложение с Micrometer и `micrometer-registry-prometheus` выставляет метрики по эндпоинту `/actuator/prometheus` в текстовом формате Prometheus.
2.  **Скрапинг**: Prometheus настроен на периодический опрос (scraping) этого эндпоинта. Он сохраняет полученные данные в своей временной базе данных.
3.  **Визуализация**: **Grafana** — популярный инструмент для визуализации данных. Она может подключаться к Prometheus как источнику данных и строить интерактивные дашборды на основе собранных метрик.

### 7.2. Пример метрик в Prometheus формате

```
# HELP orders_created_total_total Общее количество созданных заказов
# TYPE orders_created_total_total counter
orders_created_total_total{application="orderhub",environment="dev",host="my-app-01",status="success",} 15.0
orders_created_total_total{application="orderhub",environment="dev",host="my-app-01",status="failed",} 2.0

# HELP orders_creation_duration_seconds Время, затраченное на создание заказа
# TYPE orders_creation_duration_seconds summary
orders_creation_duration_seconds_count{application="orderhub",environment="dev",host="my-app-01",} 17.0
orders_creation_duration_seconds_sum{application="orderhub",environment="dev",host="my-app-01",} 0.85
orders_creation_duration_seconds{application="orderhub",environment="dev",host="my-app-01",quantile="0.5",} 0.03
orders_creation_duration_seconds{application="orderhub",environment="dev",host="my-app-01",quantile="0.95",} 0.08
orders_creation_duration_seconds{application="orderhub",environment="dev",host="my-app-01",quantile="0.99",} 0.12
```

---

## 8. Лучшие практики и соображения

### 8.1. Именование метрик

*   **Точки как разделители**: Используйте `.` (точку) для разделения компонентов имени (например, `domain.subdomain.metric_name`).
*   **Единицы измерения**: Добавляйте суффиксы, указывающие на единицы измерения (например, `.total` для счетчиков, `.seconds` для таймеров, `.bytes` для размеров).
*   **Согласованность**: Придерживайтесь единых соглашений об именовании во всем проекте.

### 8.2. Управление кардинальностью

*   **Избегайте высокой кардинальности**: Никогда не используйте уникальные идентификаторы (UUID, ID пользователей, полные URL с параметрами) в качестве значений тегов.
*   **Нормализуйте теги**: Преобразуйте высококардинальные значения в ограниченный набор категорий (например, вместо конкретного ID пользователя — `user_type:premium`).
*   **Используйте `MeterFilter`**: Активно применяйте фильтры для игнорирования или трансформации тегов с высокой кардинальностью.

### 8.3. Производительность

*   **Низкие накладные расходы**: Micrometer разработан с учетом минимального влияния на производительность. Создание и обновление метрик очень эффективно.
*   **Регистрация при старте**: Регистрируйте все измерители при старте приложения (например, в конструкторе или `@PostConstruct` методе), чтобы избежать накладных расходов на создание объектов во время выполнения критически важных операций.

### 8.4. Тестирование метрик

*   Используйте `SimpleMeterRegistry` в тестах для проверки, что метрики регистрируются с правильными именами и тегами.
*   Проверяйте значения счетчиков и таймеров после выполнения операций.

---

## 9. Заключение

Micrometer — это не просто библиотека, это мощный инструмент, который позволяет разработчикам активно участвовать в процессе наблюдаемости. Внедряя бизнес-метрики, мы создаем общий язык между разработкой, эксплуатацией и бизнесом. Это позволяет не только быстро реагировать на технические проблемы, но и оперативно выявлять и устранять проблемы, влияющие на ключевые бизнес-показатели.

Используя Micrometer, вы делаете свои приложения более прозрачными, надежными и готовыми к вызовам production-среды.

---

## 10. Ссылки и дополнительные материалы

*   [Официальная документация Micrometer](https://docs.micrometer.io/micrometer/reference/)
*   [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
*   [Baeldung: Micrometer with Spring Boot](https://www.baeldung.com/spring-boot-micrometer)
*   [Prometheus Naming Best Practices](https://prometheus.io/docs/practices/naming/)

