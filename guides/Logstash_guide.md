# Logstash

**Автор:** Oleborn

**Дата:** 01 марта 2026 г.

**Проект:** [OrderHub](https://github.com/Oleborn/OrderHub)

Logstash — это мощный, гибкий и расширяемый конвейер обработки данных с открытым исходным кодом, который является ключевым компонентом ELK-стека. Его основная задача — собирать данные из различных источников, трансформировать их в нужный формат и отправлять в различные хранилища, такие как Elasticsearch. В контексте Observability, Logstash играет роль центрального хаба для агрегации и нормализации логов, метрик и других событий.

## 1. Фундаментальные концепции и архитектура

Logstash работает как конвейер (pipeline), состоящий из трех основных этапов: Input, Filter и Output.

### 1.1. Архитектура конвейера (Pipeline architecture)

*   **Input (Вход)**: Плагины ввода отвечают за сбор данных из различных источников. Logstash поддерживает широкий спектр входных данных, включая:
    *   `file`: Чтение из файлов (например, логов).
    *   `beats`: Прием данных от Filebeat, Metricbeat и других Beats.
    *   `tcp`/`udp`: Прием данных по сетевым протоколам.
    *   `http`/`http_poller`: Прием данных по HTTP или опрос HTTP-эндпоинтов.
    *   `kafka`/`rabbitmq`: Интеграция с брокерами сообщений.
    *   `jdbc`: Периодический опрос баз данных.

*   **Filter (Фильтр)**: Плагины фильтрации трансформируют и обогащают данные по мере их прохождения через конвейер. Это сердце Logstash, где происходит основная логика обработки:
    *   `grok`: Парсинг неструктурированных логов в структурированные поля.
    *   `json`: Парсинг JSON-строк в объекты.
    *   `mutate`: Изменение полей (переименование, удаление, добавление, замена).
    *   `date`: Парсинг строк даты в объекты `timestamp`.
    *   `geoip`: Обогащение данных географической информацией по IP-адресу.
    *   `dissect`: Более простой и быстрый парсер, чем Grok, для структурированных логов с фиксированными разделителями.

*   **Output (Вывод)**: Плагины вывода отправляют обработанные данные в различные хранилища или системы. Наиболее распространенные выводы:
    *   `elasticsearch`: Отправка данных в Elasticsearch.
    *   `file`: Запись данных в файл.
    *   `stdout`: Вывод данных в консоль (полезно для отладки).
    *   `kafka`/`rabbitmq`: Отправка данных в брокеры сообщений.
    *   `s3`: Отправка данных в Amazon S3.

### 1.2. Очереди (Queues) и надежность

Logstash использует внутренние очереди для буферизации событий между этапами конвейера, что обеспечивает надежность и устойчивость к пиковым нагрузкам.

*   **In-Memory Queue (Очередь в памяти)**: По умолчанию Logstash использует очередь в памяти. Это быстро, но данные могут быть потеряны при сбое Logstash.

*   **Persistent Queue (Постоянная очередь)**: Для обеспечения надежности в production-средах рекомендуется использовать постоянную очередь. Она сохраняет события на диск, гарантируя, что данные не будут потеряны при перезапуске или сбое Logstash. Это критически важно для логов, которые не должны быть потеряны.

    ```yaml
    # logstash.yml
    queue.type: persisted
    queue.max_bytes: 1024mb # Максимальный размер очереди на диске
    queue.checkpoint.writes: 1024 # Количество записей между контрольными точками
    ```

### 1.3. Рабочие потоки (Workers) и производительность

Logstash использует несколько рабочих потоков для параллельной обработки событий. Количество рабочих потоков можно настроить с помощью параметра `pipeline.workers`.

*   `pipeline.workers`: Определяет количество потоков, которые будут выполнять фильтрацию и вывод. По умолчанию равно количеству ядер CPU. Оптимальное значение зависит от характера нагрузки (I/O-bound vs CPU-bound).

## 2. Написание конфигураций Logstash

Конфигурационный файл Logstash (обычно `.conf`) использует специальный DSL (Domain Specific Language) для определения конвейера.

### 2.1. Базовая структура

```logstash
input {
  # Плагины ввода
}

filter {
  # Плагины фильтрации
}

output {
  # Плагины вывода
}
```

### 2.2. Условная логика (Conditional Logic)

Logstash позволяет применять фильтры и выводы на основе условий, что очень полезно для маршрутизации данных.

```logstash
filter {
  if [type] == "access_log" {
    grok { match => { "message" => "%{COMBINEDAPACHELOG}" } }
  } else if [type] == "app_log" {
    json { source => "message" }
  }
}

output {
  if "_grokparsefailure" in [tags] {
    file { path => "/var/log/logstash/grok_failures.log" }
  } else {
    elasticsearch { hosts => ["elasticsearch:9200"] }
  }
}
```

## 3. Плагины фильтрации

Плагины фильтрации — это сердце Logstash, где происходит основная трансформация данных.

### 3.1. Grok: Парсинг неструктурированных логов

`grok` — это мощный инструмент для парсинга неструктурированных строк (например, логов) в структурированные поля. Он использует регулярные выражения, но предоставляет набор предопределенных паттернов для упрощения работы.

*   **Синтаксис**: `%{PATTERN:field_name:data_type}`
    *   `PATTERN`: Имя предопределенного паттерна (например, `IP`, `NUMBER`, `WORD`).
    *   `field_name`: Имя поля, в которое будет записано извлеченное значение.
    *   `data_type`: Опциональный тип данных (например, `int`, `float`). По умолчанию `string`.

*   **Пример**: Парсинг Apache Access Log
    ```logstash
    filter {
      grok {
        match => { "message" => "%{IPORHOST:clientip} %{USER:ident} %{USER:auth} \[ %{HTTPDATE:timestamp} \] \"%{WORD:verb} %{HTTPURI:request} HTTP/%{NUMBER:httpversion}\" %{NUMBER:response} %{NUMBER:bytes}"
        }
      }
    }
    ```

*   **Отладка Grok**: Используйте Grok Debugger (например, [Grok Debugger](https://grokdebug.com/)) для тестирования паттернов.

### 3.2. JSON: работа со структурированными логами

`json` фильтр используется для парсинга JSON-строк в структурированные объекты Logstash.

*   **Пример**: Парсинг JSON-лога
    ```logstash
    filter {
      json {
        source => "message" # Поле, содержащее JSON-строку
        target => "_json"   # Опциональное поле для сохранения JSON-объекта
        remove_field => ["message"] # Удалить исходное поле message
      }
    }
    ```

### 3.3. Mutate: манипуляции с полями

`mutate` — это универсальный фильтр для изменения полей.

*   **`add_field` / `remove_field`**: Добавление/удаление полей.
*   **`rename`**: Переименование полей.
*   **`convert`**: Изменение типа данных поля.
*   **`split` / `join`**: Разделение/объединение строк.
*   **`gsub`**: Замена подстрок с помощью регулярных выражений.

*   **Пример**: Очистка и преобразование полей
    ```logstash
    filter {
      mutate {
        remove_field => ["@version", "host"]
        rename => { "[event][original]" => "raw_log" }
        convert => { "response_time_ms" => "integer" }
      }
    }
    ```

### 3.4. Date: нормализация временных меток

`date` фильтр парсит строки даты в стандартный формат ISO8601 и устанавливает `@timestamp` — обязательное поле для Elasticsearch.

*   **Пример**: Парсинг различных форматов даты
    ```logstash
    filter {
      date {
        match => [ "timestamp", "dd/MMM/yyyy:HH:mm:ss Z", "ISO8601" ]
        target => "@timestamp"
      }
    }
    ```

### 3.5. GeoIP: обогащение географическими данными

`geoip` фильтр добавляет географическую информацию (страна, город, координаты) на основе IP-адреса.

*   **Пример**: Обогащение по IP-адресу клиента
    ```logstash
    filter {
      geoip {
        source => "clientip"
        target => "[geoip]"
      }
    }
    ```

## 4. Оптимизация и производительность Logstash

Эффективная работа Logstash требует внимания к производительности и надежности.

### 4.1. Настройка JVM и памяти

*   **Heap Size**: Установите `LS_HEAP_SIZE` (например, `LS_HEAP_SIZE="1g"`) в `jvm.options`. Рекомендуется выделять от 1 до 8 ГБ, в зависимости от нагрузки. Слишком большой Heap может привести к долгим паузам GC.
*   **GC (Garbage Collection)**: Мониторинг GC является ключевым. Длинные паузы GC могут привести к задержкам в обработке событий.

### 4.2. Настройка конвейера

*   **`pipeline.workers`**: Экспериментируйте с количеством рабочих потоков. Если фильтры CPU-интенсивны (например, Grok), увеличьте количество. Если I/O-интенсивны (например, чтение файлов), возможно, потребуется меньше.
*   **`pipeline.batch.size`**: Количество событий, обрабатываемых одним рабочим потоком за раз. Увеличение может повысить пропускную способность, но увеличит задержку.
*   **`pipeline.batch.delay`**: Максимальное время ожидания для заполнения пакета. Уменьшение уменьшает задержку, но может снизить пропускную способность.

### 4.3. Мониторинг Logstash

*   **Monitoring API**: Logstash предоставляет HTTP API для получения метрик (JVM, очереди, пропускная способность плагинов).
*   **X-Pack Monitoring**: Встроенный инструмент в Kibana для мониторинга Logstash.
*   **Prometheus Exporter**: Можно использовать плагин `metrics` для экспорта метрик Logstash в Prometheus.

## 5. Расширенные сценарии использования

Logstash — это не только про логи. Его гибкость позволяет решать множество задач.

### 5.1. Мульти-конвейеры (Multiple Pipelines)

Logstash может запускать несколько независимых конвейеров, каждый со своей конфигурацией, входными данными, фильтрами и выводами. Это полезно для разделения логики обработки разных типов данных или для изоляции критически важных потоков.

```yaml
# pipelines.yml
- pipeline.id: main_logs
  path.config: "/etc/logstash/conf.d/main_logs.conf"
  pipeline.workers: 4
- pipeline.id: metrics_processing
  path.config: "/etc/logstash/conf.d/metrics.conf"
  pipeline.workers: 2
```

### 5.2. Ruby Filter

Для сложных трансформаций, которые невозможно реализовать стандартными фильтрами, можно использовать `ruby` фильтр.

*   **Пример**: Вычисление кастомного поля
    ```logstash
    filter {
      ruby {
        code => "event.set('response_category', event.get('response').to_i / 100)"
      }
    }
    ```

### 5.3. Dead Letter Queue (DLQ)

DLQ — это механизм для сохранения событий, которые не удалось обработать (например, из-за ошибок парсинга или проблем с выводом). Это предотвращает потерю данных и позволяет анализировать причины сбоев.

```yaml
# logstash.yml
dead_letter_queue.enable: true
dead_letter_queue.path: /var/lib/logstash/dead_letter_queue
dead_letter_queue.max_bytes: 1024mb
```

## 6. Интеграция с экосистемой Observability

Logstash является связующим звеном между источниками данных и аналитическими платформами.

### 6.1. Сбор логирования Spring Boot с `traceId`

Logstash может принимать JSON-логи от Spring Boot приложений, которые уже содержат `traceId` и `spanId`.

*   **Logback Configuration (Spring Boot)**:
    ```xml
    <!-- logback-spring.xml -->
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <message>message</message>
                <logger>logger_name</logger>
                <thread>thread_name</thread>
                <level>log_level</level>
                <levelValue>level_value</levelValue>
                <stackTrace>stack_trace</stackTrace>
                <version>@version</version>
            </fieldNames>
            <customFields>{"application":"order-service"}</customFields>
        </encoder>
    </appender>
    ```

*   **Logstash Configuration (Input & Filter)**:
    ```logstash
    input {
      tcp { port => 5000 codec => json }
    }
    filter {
      # Дополнительные фильтры, если нужны
    }
    output {
      elasticsearch { hosts => ["elasticsearch:9200"] }
    }
    ```

### 6.2. Интеграция с Prometheus и Grafana

Хотя Logstash в основном используется для логов, его можно использовать для обработки и отправки метрик в Elasticsearch, которые затем визуализируются в Grafana. Например, можно парсить метрики из текстовых логов и преобразовывать их в структурированные документы.

### Ссылки
[1] [Logstash Reference - Elastic](https://www.elastic.co/guide/en/logstash/current/index.html)
[2] [Logstash Persistent Queues - Elastic](https://www.elastic.co/guide/en/logstash/current/persistent-queues.html)
[3] [Logstash Grok Filter Plugin - Elastic](https://www.elastic.co/guide/en/logstash/current/plugins-filters-grok.html)
