# Camunda

**Автор:** Oleborn

**Дата:** 11 июля 2026 г.

**Проект:** [OrderHub](https://github.com/Oleborn/OrderHub)

## 1. История

### 1.1. Генеалогия BPM-движков: от jBPM до Camunda 8

История Camunda — это история борьбы за здравый смысл в автоматизации бизнес-процессов.

1. **Эра "Проприетарных монстров" (1990-е - 2000-е)**:
- **Вендоры**: IBM, Oracle, SAP, Pega.
- **Проблема**: Системы стоили миллионы долларов, требовали гигантских серверов и продвигали концепцию "Zero-Code". На практике это означало, что разработчики были заперты в неудобных визуальных редакторах, а бизнес-аналитики все равно не могли создать ничего работающего без помощи IT.

1. **Рождение jBPM (2003)**:
- **Основатель**: Том Байенс (Tom Baeyens).
- **Революция**: Первый Open Source движок на Java. Он был легковесным и позволял разработчикам внедрять Java-код прямо в узлы процесса. Однако язык описания (jPDL) был закрытым стандартом.

1. **Activiti (2010)**:
- Байенс уходит из Red Hat и вместе с Alfresco создает Activiti.
- **Главный вклад**: Переход на международный стандарт **BPMN 2.0**. Это позволило переносить схемы процессов между разными инструментами.

1. **Camunda (2013)**:
- Компания Camunda Services GmbH, будучи главным консалтинговым партнером Activiti, понимает, что проект Activiti стагнирует в плане качества кода и Enterprise-возможностей.
- **18 марта 2013 года**: Объявляется о создании форка Camunda BPM 7.0. Фокус смещается на стабильность, надежность и мощные инструменты мониторинга (Cockpit).

1. **Camunda 8 (2022)**:
- Понимание того, что реляционные БД (PostgreSQL, Oracle) не могут бесконечно масштабироваться горизонтально, привело к созданию **Zeebe** — движка на базе Event Streaming (аналог Kafka для процессов).

---

## 2. Архитектура

### 2.1. PVM (Process Virtual Machine)

PVM — это "ассемблер" для процессов. Она оперирует понятиями:

- **Activity**: Узел графа.

- **Transition**: Переход между узлами.

- **Scope**: Область видимости (например, подпроцесс).

Когда движок исполняет BPMN, он транслирует его в операции PVM. Это позволяет Camunda быть невероятно гибкой и поддерживать сложные сценарии, такие как динамическое изменение процесса на лету.

### 2.2. Persistence Layer

Camunda 7 использует MyBatis. Схема БД состоит из ~40 таблиц. Ключевые из них:

#### 2.2.1. Таблицы репозитория (ACT_RE_*) — хранилище определений

Таблицы с префиксом `RE` (Repository) содержат статическую информацию, которая не меняется во время выполнения процесса.

- **ACT_RE_PROCDEF (Process Definitions)**:Эта таблица является центральным реестром всех версий процессов.

   ```sql
   CREATE TABLE ACT_RE_PROCDEF (
       ID_ varchar(64) NOT NULL,          -- Уникальный внутренний ID (обычно key:version:id)
       REV_ integer,                      -- Версия строки для Optimistic Locking
       CATEGORY_ varchar(255),            -- Пространство имен из BPMN (targetNamespace)
       NAME_ varchar(255),                -- Человекочитаемое имя процесса
       KEY_ varchar(255) NOT NULL,        -- Бизнес-ключ (ID из BPMN XML)
       VERSION_ integer NOT NULL,         -- Порядковый номер версии
       DEPLOYMENT_ID_ varchar(64),        -- Ссылка на конкретный деплой
       RESOURCE_NAME_ varchar(4000),      -- Имя .bpmn файла в ресурсах
       DGRM_RESOURCE_NAME_ varchar(4000), -- Имя файла диаграммы (.png/.svg)
       HAS_START_FORM_KEY_ tinyint,       -- Флаг наличия формы на старте
       SUSPENSION_STATE_ integer,         -- Состояние: 1 (Active), 2 (Suspended)
       TENANT_ID_ varchar(64),            -- ID арендатора для Multi-tenancy
       VERSION_TAG_ varchar(64),          -- Кастомная метка версии
       HISTORY_TTL_ integer,              -- Время жизни истории (Time To Live) в днях
       STARTABLE_ boolean DEFAULT TRUE,   -- Можно ли запустить процесс вручную
       PRIMARY KEY (ID_)
   );
   CREATE INDEX ACT_IDX_PROCDEF_KEY ON ACT_RE_PROCDEF(KEY_);
   ```

- **ACT_RE_DEPLOYMENT**:Запись создается при каждом нажатии кнопки "Deploy" в Modeler или при старте приложения.

   ```sql
   CREATE TABLE ACT_RE_DEPLOYMENT (
       ID_ varchar(64) NOT NULL,
       NAME_ varchar(255),
       DEPLOY_TIME_ timestamp,
       SOURCE_ varchar(255),              -- Источник: "process-application", "rest", etc.
       TENANT_ID_ varchar(64),
       PRIMARY KEY (ID_)
   );
   ```

#### 2.2.2. Таблицы исполнения (ACT_RU_*) — текущее состояние

Префикс `RU` (Runtime) означает, что данные здесь живут только пока процесс не завершен. Это самые нагруженные таблицы.

- **ACT_RU_EXECUTION**:Самая сложная и важная таблица. Она представляет собой дерево. Если в процессе есть параллельный шлюз, у одного `PROC_INST_ID_` будет несколько строк в этой таблице.

   ```sql
   CREATE TABLE ACT_RU_EXECUTION (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       PROC_INST_ID_ varchar(64),         -- ID инстанса (корневой узел)
       BUSINESS_KEY_ varchar(255),        -- Ваш уникальный ID (например, номер заказа)
       PARENT_ID_ varchar(64),            -- Ссылка на родительский поток исполнения
       PROC_DEF_ID_ varchar(64),          -- Ссылка на определение процесса
       ACT_ID_ varchar(255),              -- Текущий ID элемента из BPMN (например, "Task_1")
       IS_ACTIVE_ boolean,                -- Выполняется ли сейчас логика
       IS_CONCURRENT_ boolean,            -- Является ли поток параллельным
       IS_SCOPE_ boolean,                 -- Является ли поток областью видимости
       IS_EVENT_SCOPE_ boolean,
       SUSPENSION_STATE_ integer,
       CACHED_ENT_STATE_ integer,         -- Битовая маска для оптимизации (наличие переменных, задач и т.д.)
       PRIMARY KEY (ID_)
   );
   CREATE INDEX ACT_IDX_EXEC_BUSKEY ON ACT_RU_EXECUTION(BUSINESS_KEY_);
   ```

- **ACT_RU_VARIABLE**:Здесь хранятся данные, которыми оперирует процесс.

   ```sql
   CREATE TABLE ACT_RU_VARIABLE (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       TYPE_ varchar(255) NOT NULL,       -- string, integer, double, boolean, null, binary, serializable
       NAME_ varchar(255) NOT NULL,
       EXECUTION_ID_ varchar(64),         -- Ссылка на поток исполнения
       PROC_INST_ID_ varchar(64),         -- Ссылка на инстанс процесса
       CASE_EXECUTION_ID_ varchar(64),
       CASE_INST_ID_ varchar(64),
       TASK_ID_ varchar(64),              -- Если переменная локальна для задачи
       BYTEARRAY_ID_ varchar(64),         -- Ссылка на бинарные данные (ACT_GE_BYTEARRAY)
       DOUBLE_ double precision,
       LONG_ bigint,
       TEXT_ varchar(4000),               -- Значение для строк и примитивов
       TEXT2_ varchar(4000),              -- Дополнительное поле для метаданных
       PRIMARY KEY (ID_)
   );
   ```

#### 2.2.3. Таблицы истории (ACT_HI_*) — след в истории

Когда процесс завершается, данные из `RU` таблиц удаляются, но в `HI` (History) они остаются для аудита и аналитики.

- **ACT_HI_PROCINST**: История инстансов процессов.

   ```sql
   CREATE TABLE ACT_HI_PROCINST (
       ID_ varchar(64) NOT NULL,
       PROC_INST_ID_ varchar(64) NOT NULL,
       BUSINESS_KEY_ varchar(255),
       PROC_DEF_KEY_ varchar(255),
       PROC_DEF_ID_ varchar(64) NOT NULL,
       START_TIME_ datetime NOT NULL,
       END_TIME_ datetime,
       DURATION_ bigint,
       START_USER_ID_ varchar(255),
       START_ACT_ID_ varchar(255),
       END_ACT_ID_ varchar(255),
       SUPER_PROCESS_INSTANCE_ID_ varchar(64),
       DELETE_REASON_ varchar(4000),
       TENANT_ID_ varchar(64),
       STATE_ varchar(255),
       PRIMARY KEY (ID_)
   );
   ```

- **ACT_HI_ACTINST**: Лог прохождения каждого узла.

- **ACT_HI_VARINST**: История переменных.

- **ACT_HI_TASKINST**: История пользовательских задач.

- **ACT_HI_DETAIL**: Самая детальная таблица (логи всех изменений переменных, форм и т.д.). **Внимание**: При уровне истории `full` эта таблица растет катастрофически быстро.

- **ACT_HI_COMMENT**: Комментарии пользователей к задачам.

- **ACT_HI_ATTACHMENT**: Ссылки на вложения.

#### 2.2.4. Общие таблицы (ACT_GE_*) — системные свойства

- **ACT_GE_BYTEARRAY**:Хранит все бинарные ресурсы. Если вы передаете в процесс Java-объект, он будет сериализован и сохранен здесь.

   ```sql
   CREATE TABLE ACT_GE_BYTEARRAY (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       NAME_ varchar(255),                -- Имя ресурса (например, process.bpmn)
       DEPLOYMENT_ID_ varchar(64),        -- Ссылка на деплой
       BYTES_ longblob,                   -- Сами данные
       GENERATED_ tinyint,                -- Флаг: сгенерировано движком или загружено пользователем
       PRIMARY KEY (ID_)
   );
   ```

- **ACT_GE_PROPERTY**:Хранит глобальные свойства, такие как версия схемы БД. Если версия в БД не совпадает с версией библиотеки, движок не запустится.

#### 2.2.5. Таблицы Идентификации (ACT_ID_*) — пользователи и группы

Хотя в Enterprise-среде эти таблицы часто пустуют (используется LDAP), они важны для понимания модели безопасности Camunda.

- **ACT_ID_USER**: Учетные записи.

- **ACT_ID_GROUP**: Роли и департаменты.

- **ACT_ID_MEMBERSHIP**: Связь "многие-ко-многим" между пользователями и группами.

- **ACT_ID_TENANT**: Арендаторы для Multi-tenancy.

#### 2.2.6. Таблицы заданий (ACT_RU_JOB / ACT_RU_TIMER_JOB / ACT_RU_SUSPENDED_JOB / ACT_RU_DEADLETTER_JOB)

Это "очередь задач" внутри Camunda. Каждая таблица имеет свою специфику:

- **ACT_RU_JOB**:Задания, которые Job Executor должен забрать немедленно.

   ```sql
   CREATE TABLE ACT_RU_JOB (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       TYPE_ varchar(255) NOT NULL,       -- message, timer
       LOCK_EXP_TIME_ timestamp,          -- Время, до которого задача заблокирована воркером
       LOCK_OWNER_ varchar(255),          -- ID узла, взявшего задачу
       EXCLUSIVE_ boolean,                -- Флаг эксклюзивности
       EXECUTION_ID_ varchar(64),
       PROCESS_INSTANCE_ID_ varchar(64),
       RETRIES_ integer,                  -- Оставшееся количество попыток
       EXCEPTION_STACK_ID_ varchar(64),   -- Ссылка на стек ошибки в BYTEARRAY
       EXCEPTION_MSG_ varchar(4000),
       DUEDATE_ timestamp,                -- Когда выполнять
       REPEAT_ varchar(255),              -- Для повторяющихся таймеров
       HANDLER_TYPE_ varchar(255),        -- async-continuation, timer-transition и т.д.
       HANDLER_CFG_ varchar(4000),
       PRIMARY KEY (ID_)
   );
   ```

- **ACT_RU_TIMER_JOB**:Здесь лежат будущие таймеры. Когда время наступает, Job Executor переносит запись из этой таблицы в `ACT_RU_JOB`.

- **ACT_RU_SUSPENDED_JOB**:Если вы приостановили инстанс процесса, все его активные задания переносятся сюда, чтобы Job Executor их не трогал.

- **ACT_RU_DEADLETTER_JOB**:Если `RETRIES_` в `ACT_RU_JOB` становится равным 0, задача переносится сюда. Она больше никогда не будет выполнена автоматически. Требуется ручное вмешательство через Cockpit или REST API.

#### 2.2.7. Таблицы фильтров и авторизаций (ACT_RU_FILTER / ACT_RU_AUTHORIZATION)

- **ACT_RU_FILTER**: Хранит сохраненные фильтры для задач в Tasklist.

- **ACT_RU_AUTHORIZATION**:Центральная таблица безопасности.

   ```sql
   CREATE TABLE ACT_RU_AUTHORIZATION (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       TYPE_ integer NOT NULL,            -- 0 (Global), 1 (Grant), 2 (Revoke)
       GROUP_ID_ varchar(255),
       USER_ID_ varchar(255),
       RESOURCE_TYPE_ integer NOT NULL,   -- ProcessDefinition (6), Task (7), etc.
       RESOURCE_ID_ varchar(255),         -- ID ресурса или "*" для всех
       PERMS_ integer,                    -- Битовая маска прав (READ, UPDATE, CREATE...)
       PRIMARY KEY (ID_)
   );
   ```

#### 2.2.8. Таблицы инцидентов и внешних задач (ACT_RU_INCIDENT / ACT_RU_EXT_TASK)

- **ACT_RU_INCIDENT**:Запись о возникшей проблеме (например, провал всех попыток выполнения Job или ошибка в BPMN).

- **ACT_RU_EXT_TASK**:Хранит состояние задач, которые выполняются внешними воркерами (External Task Pattern).

   ```sql
   CREATE TABLE ACT_RU_EXT_TASK (
       ID_ varchar(64) NOT NULL,
       REV_ integer,
       WORKER_ID_ varchar(255),           -- Кто заблокировал задачу
       TOPIC_NAME_ varchar(255),          -- Тема (Topic) для подписки
       RETRIES_ integer,
       ERROR_MSG_ varchar(4000),
       LOCK_EXP_TIME_ timestamp,
       SUSPENSION_STATE_ integer,
       PRIMARY KEY (ID_)
   );
   ```

#### 2.2.9. Таблицы пакетных операций (ACT_RU_BATCH / ACT_HI_BATCH)

Используются для массовых действий над тысячами инстансов (например, массовое удаление или миграция).

- **ACT_RU_BATCH**: Текущие пакетные задания.

- **ACT_HI_BATCH**: История пакетных заданий.

#### 2.2.10. Таблицы DMN (ACT_RE_DECISION_DEF / ACT_RE_DECISION_TABLE)

Хранят определения таблиц принятия решений.

- **ACT_RE_DECISION_DEF**: Метаданные DMN (ключ, версия).

- **ACT_RE_DECISION_REQ_DEF**: Группировка нескольких DMN в одну DRD (Decision Requirement Diagram).

---

## 2.3. Жизненный цикл команды (Command Pattern Internals)

Camunda построена на паттерне "Команда". Любое действие (запуск процесса, завершение задачи) — это `Command`.

1. **CommandExecutor**: Принимает команду.

1. **Interceptor Chain**: Цепочка перехватчиков.
- `LogInterceptor`: Логирует начало.
- `ExceptionInterceptor`: Ловит и классифицирует ошибки.
- `CommandContextInterceptor`: Создает `CommandContext` (открывает транзакцию и сессию БД).
- `TransactionContextInterceptor`: Управляет интеграцией с JTA/Spring Transaction.

1. **CommandContext**: Хранит текущую сессию MyBatis, кэш сущностей и список отложенных операций.

1. **AtomicOperation**: Команда разбивается на атомарные шаги (например, "перейти к следующему узлу", "выполнить делегат").

**Почему это важно?**Это позволяет Camunda гарантировать, что либо весь шаг процесса выполнится и сохранится в БД, либо (при ошибке) транзакция откатится, и процесс вернется в последнюю стабильную точку.

#### Таблицы истории (ACT_HI_*)

Данные сюда попадают асинхронно или в той же транзакции (зависит от настроек).

- **ACT_HI_PROCINST**: Завершенные и текущие процессы.

- **ACT_HI_ACTINST**: История каждого шага. Позволяет строить Heatmaps в Cockpit.

### 2.3. Job Executor: механика асинхронности

Job Executor состоит из трех частей:

1. **Job Acquisition Thread**: Опрашивает таблицу `ACT_RU_JOB`.

1. **Thread Pool**: Выполняет джобы.

1. **Wait Strategy**: Определяет, как долго ждать перед следующим опросом, если джоб нет.

**Параметры тюнинга в ****`application.yaml`****:**

```yaml
camunda.bpm.job-execution:
  enabled: true
  core-pool-size: 10
  max-pool-size: 50
  queue-capacity: 100
  lock-time-in-millis: 300000 # 5 минут
  max-jobs-per-acquisition: 10
  wait-time-in-millis: 5000
```

### 2.4. Транзакционная модель и Wait States

Camunda гарантирует ACID. Состояние сохраняется только в "точках ожидания" (Wait States):

- User Task.

- Message/Signal Catch Event.

- Timer Event.

- Элементы с `asyncBefore="true"` или `asyncAfter="true"`.

**Важно**: Если между двумя Wait States происходит ошибка, вся транзакция откатывается к последнему сохраненному состоянию. Это основа надежности Camunda.

## 3. Стандарты: BPMN, DMN и CMMN

### 3.1. BPMN 2.0: Полный справочник элементов

#### 3.1.1. События (Events) — Жизненные вехи процесса

События — это точки в процессе, где что-то происходит. Они могут инициировать процесс, прерывать его или изменять путь исполнения.

**Матрица типов событий:**

- **Message Start Event (Старт по сообщению)**:Позволяет запустить процесс внешней системой.

   ```xml
   <bpmn:startEvent id="StartByMessage">
     <bpmn:messageEventDefinition id="..." messageRef="OrderReceived" />
   </bpmn:startEvent>
   ```

- **Boundary Timer Event (Граничный таймер)**:Устанавливается на задачу. Если задача не выполнена вовремя — токен уходит по ветке таймера.

   ```xml
   <bpmn:boundaryEvent id="ReminderTimer" attachedToRef="UserTask_1">
     <bpmn:timerEventDefinition id="...">
       <bpmn:timeDuration xsi:type="bpmn:tFormalExpression">PT24H</bpmn:timeDuration>
     </bpmn:timerEventDefinition>
   </bpmn:boundaryEvent>
   ```

- **Error Boundary Event (Граничная ошибка)**:Ловит исключения из Java-кода.

   ```xml
   <bpmn:boundaryEvent id="CatchError" attachedToRef="ServiceTask_1">
     <bpmn:errorEventDefinition id="..." errorRef="TechnicalError" camunda:errorCodeVariable="errCode" />
   </bpmn:boundaryEvent>
   ```

- **Signal Intermediate Throw Event (Генерация сигнала)**:Отправляет сигнал всем активным инстансам.

   ```xml
   <bpmn:intermediateThrowEvent id="BroadcastUpdate">
     <bpmn:signalEventDefinition id="..." signalRef="PriceChanged" />
   </bpmn:intermediateThrowEvent>
   ```

#### 3.1.2. Шлюзы (Gateways) — Развилки и Слияния

Шлюзы управляют логикой движения токенов.

- **Parallel Gateway (AND)**:
    - *Fork*: Разделяет один поток на несколько. Все ветки выполняются параллельно.
    - *Join*: Ждет прихода токенов по всем входящим веткам. Если одна ветка "зависла" — процесс не пойдет дальше.

- **Exclusive Gateway (XOR)**:

   ```xml
   <bpmn:exclusiveGateway id="CheckAmount" default="Flow_Default" />
   <bpmn:sequenceFlow id="Flow_High" sourceRef="CheckAmount" targetRef="ManagerApproval">
     <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${amount > 1000}</bpmn:conditionExpression>
   </bpmn:sequenceFlow>
   ```
    - *Decision*: Проверяет условия на исходящих стрелках. Первая подошедшая стрелка забирает токен.
    - *XML*:

- **Inclusive Gateway (OR)**:Самый сложный для отладки. Позволяет активировать несколько веток одновременно, если условия на них истинны. При слиянии он интеллектуально ждет только те ветки, которые были активированы на входе.

#### 3.1.3. Задачи (Tasks)

- **Service Task (Сервисная задача)**:Основной инструмент автоматизации. В Camunda 7 есть три способа вызова:
    1. **Java Delegate**: `${myDelegate}`.
    1. **Expression**: `${myService.doWork(execution)}`.
    1. **External Task**: `type="external" topic="myTopic"`.

- **User Task (Пользовательская задача)**:Создает запись в `ACT_RU_TASK`.
    - `Assignee`: Конкретный исполнитель.
    - `Candidate Groups`: Список ролей.
    - `Due Date`: Срок выполнения.

- **Call Activity (Вызов подпроцесса)**:Позволяет переиспользовать другие BPMN схемы. Это основа декомпозиции больших процессов.

   ```xml
   <bpmn:callActivity id="ProcessPayment" calledElement="payment-process-v2">
     <bpmn:extensionElements>
       <camunda:in source="orderId" target="p_orderId" />
       <camunda:out source="p_status" target="orderStatus" />
     </bpmn:extensionElements>
   </bpmn:callActivity>
   ```

#### Шлюзы (Gateways)

- **Exclusive (XOR)**: Логика "IF-ELSE". Только один путь.

- **Parallel (AND)**: Разделение и слияние потоков. Слияние ждет ВСЕ входящие токены.

- **Inclusive (OR)**: Комбинация XOR и AND. Слияние ждет только те токены, которые теоретически могут дойти.

- **Event-based**: Путь определяется тем, какое событие произойдет первым.

#### Задачи (Tasks)

- **Service Task**: Вызов логики.
    - *Java Delegate*: Прямой вызов.
    - *External Task*: Паттерн "ящик для писем". Воркер забирает задачу через REST.

- **User Task**: Работа для человека. Появляется в Tasklist.

- **Business Rule Task**: Вызов DMN таблицы.

---

### 3.2. DMN 1.3: таблицы принятия решений

DMN (Decision Model and Notation) — это способ описания логики без кода.

#### Hit Policies (Политики совпадения)

1. **Unique (U)**: Только одно правило может сработать.

1. **First (F)**: Берется первое сработавшее правило (порядок важен).

1. **Collect (C)**: Собирает все результаты в список.
- `C+`: Сумма.
- `C#`: Количество.

1. **Any (A)**: Несколько правил могут сработать, но они должны возвращать одинаковый результат.

#### FEEL (Friendly Enough Expression Language)

Это язык выражений внутри DMN. Примеры:

- `[10..20]` — число в диапазоне.

- `< 50` — меньше 50.

- `"Gold", "Silver"` — вхождение в список строк.

- `date and time("2026-07-08T12:00:00")` — работа с датами.

---

### 3.3. CMMN 1.1: Управление кейсами (Case Management)

CMMN используется для слабоструктурированных процессов, где порядок действий определяет человек, а не жесткая схема.

- **Stage**: Этап кейса.

- **Sentry**: Условие входа/выхода ("On Part" + "If Part").

- **Milestone**: Достижение цели.

- **Human Task**: Действие, которое может быть активировано вручную.

**Примечание**: Camunda 8 больше не поддерживает CMMN. Рекомендуется моделировать кейсы через гибкие BPMN структуры или внешнюю логику.

### 3.4. Сравнение архитектурных паттернов развертывания

| Паттерн | Описание | Плюсы | Минусы |
| --- | --- | --- | --- |
| **Embedded Engine** | Движок внутри вашего Spring Boot приложения. | Максимальная скорость, общие транзакции с вашей БД. | Трудно масштабировать отдельно от приложения. |
| **Shared Engine** | Движок как сервис в Tomcat/Wildfly. | Несколько приложений используют один Engine. | Сложен в обновлении и изоляции. |
| **Remote Engine (REST)** | Camunda Run + ваши воркеры через REST/External Tasks. | Полная изоляция, полиглотность. | Сетевой оверхед, отсутствие общих транзакций. |
| **Sidecar (K8s)** | Каждому микросервису — своя Camunda рядом. | Изоляция и скорость. | Большое потребление ресурсов. |

---

### 3.5. Детальный разбор DMN: От простых условий к FEEL

DMN таблицы в Camunda — это не просто "if-then". Это мощный функциональный язык.

**Пример сложного FEEL выражения:**Вход: `age` (число), `history` (список строк).Условие: `age > 18 and list contains(history, "reliable")`Результат: `Status = "Approved"`

**Таблица маппинга типов DMN в Java:**

- `string` -> `java.lang.String`

- `integer` -> `java.lang.Integer`

- `double` -> `java.lang.Double`

- `boolean` -> `java.lang.Boolean`

- `date` -> `java.util.Date`

## 4. Разработка на Java

### 4.1. Интеграция со Spring Boot

Camunda идеально ложится на Spring Boot. Основная зависимость:

```xml
<dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter</artifactId>
    <version>7.21.0</version>
</dependency>
```

**Конфигурация ****`application.yaml`****:**

```yaml
camunda.bpm:
  admin-user:
    id: admin
    password: password
  database:
    schema-update: true
    type: postgres
  generic-properties:
    properties:
      historyCleanupBatchWindowStartTime: "00:00"
      historyCleanupBatchWindowEndTime: "06:00"
```

### 4.2. Java Delegates и Delegate Expressions

#### 4.2.1. Паттерн Java Delegate

Java Delegate — это классический способ синхронного вызова логики.

```java
@Component("calculateDiscount")
@Slf4j
public class CalculateDiscountDelegate implements JavaDelegate {

    private final DiscountService discountService;

    // Внедрение зависимостей через конструктор
    public CalculateDiscountDelegate(DiscountService discountService) {
        this.discountService = discountService;
    }

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        log.info("Executing discount calculation for process: {}", execution.getProcessInstanceId());

        // 1. Извлечение переменных (типизация важна)
        Integer orderSum = (Integer) execution.getVariable("orderSum");
        String customerType = (String) execution.getVariable("customerType");

        if (orderSum == null) {
            // Генерация бизнес-ошибки, которую можно поймать в BPMN
            throw new BpmnError("INVALID_DATA", "Order sum is missing");
        }

        // 2. Вызов внешнего сервиса
        double discount = discountService.getDiscount(orderSum, customerType);

        // 3. Сохранение результата
        execution.setVariable("calculatedDiscount", discount);

        // 4. Локальные переменные (живут только в рамках этой задачи)
        execution.setVariableLocal("tempProcessingId", UUID.randomUUID().toString());

        log.info("Discount calculated: {}%", discount * 100);
    }
}
```

#### 4.2.2. Использование Delegate Expression

В BPMN Modeler:

- `Type`: Java Delegate

- `Delegate Expression`: `${calculateDiscount}`

**Почему это лучше, чем ****`Class`****?**

- Поддержка Spring Beans (DI).

- Легче тестировать через Mockito.

- Возможность менять реализацию без изменения BPMN XML.

### 4.3. External Task Pattern

External Task — это паттерн, где Camunda не вызывает ваш код, а ваш код "опрашивает" Camunda.

**Преимущества:**

1. **Полиглотность**: Воркер может быть на Python, Go, Node.js.

1. **Backpressure**: Если воркер не справляется, задачи просто копятся в очереди, не перегружая JVM движка.

1. **Таймауты**: Если воркер упал, задача вернется в очередь по истечении `lockDuration`.

**Продвинутый Java Worker:**

```java
@Configuration
public class CamundaWorkerConfig {

    @Bean
    public ExternalTaskClient externalTaskClient() {
        return ExternalTaskClient.create()
            .baseUrl("http://camunda-engine:8080/engine-rest" )
            .asyncResponseTimeout(10000) // Long polling на 10 секунд
            .maxTasks(10)                // Забирать не более 10 задач за раз
            .build();
    }

    @PostConstruct
    public void subscribe() {
        externalTaskClient().subscribe("payment-topic")
            .lockDuration(5000) // Блокировка на 5 секунд
            .handler((task, service) -> {
                try {
                    // Логика оплаты
                    log.info("Processing payment for task: {}", task.getId());
                    service.complete(task);
                } catch (Exception e) {
                    // Обработка ошибок с созданием инцидента в Cockpit
                    service.handleFailure(task, "Payment failed", e.getMessage(), 0, 0);
                }
            })
            .open();
    }
}
```

### 4.4. Слушатели (Listeners)

Слушатели позволяют внедрять логику, не загромождая визуальную схему процесса.

- **Execution Listener**:
    - *Start*: Логирование начала шага, инициализация переменных.
    - *End*: Очистка временных данных, отправка метрик в Prometheus.
    - *Take*: Только для Sequence Flow — логика при переходе.

- **Task Listener**:
    - *Create*: Автоматическое назначение (Auto-assignment) на основе данных из БД.
    - *Assignment*: Уведомление пользователя (Email/Slack).
    - *Complete*: Валидация введенных пользователем данных перед завершением задачи.

---

### 4.5. Безопасность и Multi-tenancy

В крупных компаниях одна Camunda обслуживает разные департаменты.

1. **Tenant ID**: Каждая запись в БД помечается `TENANT_ID_`. Пользователь департамента А не видит процессы департамента Б.

1. **Identity Service**: Интеграция с LDAP или Active Directory.

   ```java
   @Bean
   public LdapIdentityProviderFactory ldapIdentityProviderFactory() {
       LdapIdentityProviderFactory factory = new LdapIdentityProviderFactory();
       factory.setServerUrl("ldap://localhost:389");
       factory.setUserSearchBase("ou=users,dc=camunda,dc=org");
       return factory;
   }
   ```

1. **Authorization Service**: Тонкая настройка прав (кто может запускать, кто может удалять, кто может видеть переменные).

---

## 5. Продвинутые расширения (SPI)

### 5.1. ProcessEnginePlugin

Плагины позволяют внедрять кастомную логику на этапе инициализации движка.

**Пример: Шифрование переменных в БД**Вы можете написать плагин, который будет автоматически шифровать чувствительные данные перед сохранением в `ACT_RU_VARIABLE`.

```java
@Component
public class EncryptionPlugin extends AbstractProcessEnginePlugin {
    @Override
    public void preInit(ProcessEngineConfigurationImpl config) {
        config.getCustomPreVariableSerializers().add(new EncryptedVariableSerializer());
    }
}
```

### 5.2. Custom History Backend (HistoryEventHandler)

В высоконагруженных системах таблицы `ACT_HI_*` становятся гигантскими. Один из способов решения — отправлять историю во внешнее хранилище.

```java
public class KafkaHistoryHandler implements HistoryEventHandler {
    private final KafkaProducer producer;

    @Override
    public void handleEvent(HistoryEvent historyEvent) {
        // Конвертация события в JSON и отправка в Kafka
        producer.send(new ProducerRecord("camunda-history", historyEvent.getId(), historyEvent));
    }

    @Override
    public void handleEvents(List<HistoryEvent> historyEvents) {
        for (HistoryEvent event : historyEvents) handleEvent(event);
    }
}
```

Для активации в Spring Boot:

```java
@Bean
public ProcessEngineConfigurationImpl processEngineConfigurationImpl(List<ProcessEnginePlugin> plugins) {
    SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
    config.setHistoryEventHandler(new KafkaHistoryHandler());
    return config;
}
```

### 5.3. Custom Incident Handler

Если вы хотите, чтобы при возникновении ошибки в процессе не просто создавался инцидент в Cockpit, но и отправлялось уведомление в PagerDuty или OpsGenie.

```java
public class PagerDutyIncidentHandler implements IncidentHandler {
    @Override
    public String getIncidentHandlerType() {
        return Incident.FAILED_JOB_HANDLER_TYPE;
    }

    @Override
    public Incident handleIncident(IncidentContext context, String message) {
        // 1. Создать инцидент в Camunda (стандартное поведение)
        Incident incident = DefaultIncidentHandler.handleIncident(context, message);
        
        // 2. Отправить алерт в PagerDuty
        sendToPagerDuty(incident.getId(), message);
        
        return incident;
    }
}
```

---

## 6. Архитектурные паттерны в Enterprise

### 5.1. Паттерн Saga (Распределенные транзакции)

Поскольку в микросервисах нет единой транзакции, Camunda выступает в роли оркестратора Саги.

1. **Forward Path**: Последовательные вызовы сервисов.

1. **Compensation Path**: Если шаг N упал, движок вызывает компенсации для шагов 1..N-1.

### 5.2. Оркестрация vs Хореография

- **Оркестрация**: Camunda знает весь процесс. Плюсы: прозрачность, контроль. Минусы: централизация.

- **Хореография**: Сервисы общаются через события (Kafka). Плюсы: децентрализация. Минусы: невозможно понять текущее состояние заказа без агрегатора логов.

**Рекомендация**: Используйте оркестрацию внутри бизнес-домена и хореографию между доменами.

### 5.3. Camunda как Sidecar в K8s

Развертывание Camunda Run рядом с вашим сервисом в одном Pod. Это обеспечивает минимальный сетевой лаг и позволяет сервису использовать Camunda как персональный движок процессов.

---

## 5.4. Справочник Camunda REST API

Для управления процессом извне используется REST API. По умолчанию доступно по адресу `/engine-rest`.

### 5.4.1. Запуск процесса

- **POST** `/process-definition/key/{key}/start`

- **Body**:

   ```json
   {
     "variables": {
       "orderId": {"value": "123", "type": "String"},
       "amount": {"value": 5000, "type": "Integer"}
     },
     "businessKey": "ORDER-123"
   }
   ```

### 5.4.2. Поиск задач

- **GET** `/task?assignee=kermit&priority=50`

- **Результат**: Список объектов задач с ID, именем и датой создания.

### 5.4.3. Завершение задачи

- **POST** `/task/{id}/complete`

- **Body**: Переменные, которые нужно обновить при завершении.

---

## 5.5. Архитектурные варианты интеграции в микросервисах

### 5.5.1. Оркестрация через External Tasks

Это "Золотой стандарт" для современных систем.

1. Camunda выставляет задачу в топик.

1. Микросервис-воркер (на любом языке) забирает задачу.

1. Микросервис выполняет работу и сообщает Camunda о результате.**Плюс**: Camunda никогда не упадет из-за того, что ваш микросервис тормозит.

### 5.5.2. Реактивная оркестрация (Spring Cloud Streams + Kafka)

1. Camunda Delegate отправляет сообщение в Kafka.

1. Микросервис слушает Kafka, делает работу и отправляет ответ в другой топик.

1. Camunda ловит ответ через `Message Intermediate Catch Event`.**Плюс**: Полная асинхронность и отказоустойчивость.

---

## 5.6. HikariCP для Camunda

Поскольку Camunda очень активно работает с БД, настройки пула соединений критичны.

```yaml
spring.datasource.hikari:
  minimum-idle: 5
  maximum-pool-size: 20
  idle-timeout: 30000
  pool-name: CamundaPool
  max-lifetime: 2000000
  connection-timeout: 30000
```

**Совет**: Всегда следите за `maximum-pool-size`. Если Job Executor имеет 50 потоков, а пул — 20, вы получите `ConnectionTimeoutException`.

## 6. Camunda 8 (Zeebe & Cloud Native)

### 6.1. Почему Camunda 8 — это революция?

В Camunda 7 узким местом была реляционная БД. При достижении миллионов инстансов, индексы переставали работать, а блокировки (locks) приводили к деградации всей системы.

**Zeebe решает это через:**

1. **Append-only Log**: Все команды и события записываются в конец файла. Это дает невероятную скорость записи (десятки тысяч событий в секунду).

1. **Partitions (Шардирование)**: Процессы распределяются по шардам. Шард 1 может обрабатываться брокером А, а шард 2 — брокером Б. Это дает горизонтальное масштабирование.

1. **No RDBMS**: Вместо тяжелых SQL запросов используется прямой доступ к RocksDB для чтения текущего состояния.

1. **Gateway & gRPC**: Клиенты подключаются к Gateway, который скрывает сложность кластера и балансирует нагрузку.

#### 6.1.1. Сравнительная таблица Camunda 7 vs Camunda 8

| Характеристика | Camunda 7 (Platform) | Camunda 8 (Cloud) |
| --- | --- | --- |
| **Движок** | Process Engine (Java) | Zeebe (Go-based/Java-friendly) |
| **Хранилище** | RDBMS (Postgres, Oracle, SQL Server) | Event Log + RocksDB + Elastic/OpenSearch |
| **Связь** | Java API / REST | gRPC |
| **Масштабирование** | Вертикальное (БД) / Горизонтальное (App) | Горизонтальное (Шардирование Zeebe) |
| **Транзакции** | ACID (общая транзакция с БД) | Eventual Consistency (Saga Pattern) |
| **Моделирование** | BPMN, DMN, CMMN | BPMN, DMN (CMMN не поддерживается) |
| **Исполнение** | Java Delegates (синхронно/асинхронно) | Job Workers (всегда асинхронно) |
| **Развертывание** | Embedded, Shared, Remote | Cloud-native, SaaS, Self-managed (K8s) |

#### 6.1.2. Внутреннее устройство Zeebe (Deep Dive)

Zeebe работает на алгоритме консенсуса **Raft**.

- **Leader**: Принимает все записи.

- **Followers**: Реплицируют лог.

- **Exporter**: Механизм, который выгружает данные из внутреннего лога в Elasticsearch для визуализации в Operate (аналог Cockpit).

- **Backpressure**: Если брокер перегружен, он возвращает клиенту ошибку `RESOURCE_EXHAUSTED`, заставляя его замедлиться. Это предотвращает падение системы под нагрузкой.

### 6.2. Процесс миграции с 7 на 8

Это не "просто обновление библиотеки". Это архитектурный проект.

- **BPMN**: 90% схем совместимы, но нужно заменить `Java Delegates` на `Job Workers`.

- **Data**: Перенос активных инстансов практически невозможен "в лоб". Рекомендуется стратегия "Drain & Switch": дождаться завершения процессов в 7-ке и запускать новые в 8-ке.

- **API**: REST API Camunda 7 заменяется на gRPC API Camunda 8.

---

## 7. Operational Excellence

### 7.1. Тюнинг Job Executor для High Load

В высоконагруженных системах стандартные настройки не работают.

- **Deployment Aware**: Обязательно `true`, если у вас несколько разных приложений используют одну БД Camunda. Это предотвратит попытки узла выполнить код, которого у него нет.

- **Wait Time**: Если у вас всегда есть работа, ставьте `waitTimeInMillis=10`. Это заставит поток опроса работать практически непрерывно.

- **Lock Duration**: Если ваша задача может идти 10 минут (например, тяжелый расчет), ставьте `lockTimeInMillis=900000` (15 мин), иначе движок решит, что воркер "умер", и отдаст задачу другому, что приведет к дублям.

### 7.2. Управление историей (History Cleanup)

Это "священный грааль" стабильности Camunda.

1. **History Level**: Если вам не нужен аудит каждой переменной, ставьте `level=audit` вместо `full`. Это сэкономит 50% места в БД.

1. **Cleanup Strategy**: Используйте `removalTime` (появилось в 7.10). Это позволяет удалять весь инстанс процесса целиком, что гораздо быстрее, чем удаление отдельных строк.

---

## 8. Энциклопедия Troubleshooting

### 8.1. Optimistic Locking Exception (OLE)

OLE — это не баг, это механизм защиты целостности данных. Но в High Load он может стать проблемой.

**Сценарии возникновения:**

1. **Параллельные ветки**: Два `Service Task` после `Parallel Gateway` выполняются быстро и одновременно пытаются завершиться. Они оба обновляют родительский токен.

1. **Таймеры и Сообщения**: Таймер сработал ровно в тот момент, когда пришло сообщение.

1. **Внешние API**: Вы обновили переменную через REST API, пока движок выполнял задачу.

**Стратегии решения:**

- **Разрыв транзакций**: Ставьте `asyncBefore=true` на элементы, которые следуют сразу за параллельным шлюзом.

- **Exclusive Gateway**: Camunda по умолчанию помечает задачи как `exclusive`. Это значит, что Job Executor не будет брать две задачи одного инстанса в разные потоки одновременно. **Никогда не выключайте это без крайней необходимости!**

- **Retry Strategy**: Настройте кастомную стратегию повторов. Для OLE часто помогает простая задержка в 50-100мс перед повтором.

### 8.2. Инциденты и Dead Letter Queues (DLQ)

В Camunda нет понятия DLQ в чистом виде, как в RabbitMQ. Вместо этого есть **Инциденты**.

- **Incident Type**: `failedJob`.

- **Configuration**: `camunda.bpm.job-execution.max-retries=3`.

- **Анализ**: В Cockpit вы видите Stacktrace. Вы можете изменить значение переменной (которая вызвала ошибку) и нажать "Retry". Это на порядок удобнее, чем ковырять сообщения в RabbitMQ.

### 8.3. Тюнинг Garbage Collector для Camunda

Camunda создает много короткоживущих объектов при парсинге BPMN и выполнении выражений.

- **Рекомендация**: Используйте **G1GC** или **ZGC** (для Java 17+).

- **Параметры**: `-XX:+UseG1GC -XX:MaxGCPauseMillis=200`.

---

## 9. Best Practices: Золотые правила архитектора Camunda

1. **Бизнес-логика vs Логика процесса**: Не пишите расчеты внутри BPMN через скрипты. BPMN должен только направлять потоки, а расчеты должны быть в Java-сервисах.

1. **Идемпотентность**: Каждый ваш Service Task должен уметь запускаться дважды без негативных последствий. Это база надежности распределенных систем.

1. **Версионирование**: Помните, что старые инстансы процесса продолжат бежать по старой версии схемы. Всегда проверяйте совместимость данных.

1. **Размер переменных**: Не храните PDF-файлы или огромные JSON в переменных процесса. Храните их в S3/Minio, а в Camunda — только ссылку (ID).

1. **Тестирование**: Используйте `camunda-bpm-assert`. Процесс без тестов — это бомба замедленного действия.

   ```java
   assertThat(processInstance).isWaitingAt("UserTask_Approve");
   complete(task());
   assertThat(processInstance).hasPassed("ServiceTask_Save").isEnded();
   ```

