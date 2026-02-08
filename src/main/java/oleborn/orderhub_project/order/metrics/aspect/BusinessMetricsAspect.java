package oleborn.orderhub_project.order.metrics.aspect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.orderhub_project.order.metrics.annotation.BusinessMetric;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BusinessMetricsAspect {

    private final MeterRegistry meterRegistry;

    // Кэшируем Timer по имени + класс + статичные теги + статус
    // Если нужен p95 отдельно для success/error — кэшируем по статусу
    private final ConcurrentMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    // @Around: advice, который полностью оборачивает метод.
    // "@annotation(metric)": применяется к методам с аннотацией @TimedBusinessMetric.
    // ProceedingJoinPoint: доступ к вызываемому методу.
    // TimedBusinessMetric: сама аннотация с параметрами (value, tags).
    @Around("@annotation(metric)")
    public Object measure(
            ProceedingJoinPoint joinPoint,
            BusinessMetric metric
    ) throws Throwable {

        // System.nanoTime(): высокоточный таймер для измерения интервалов.
        // НЕ связан с системным временем, только относительные замеры.
        // Точность: наносекунды (1/1_000_000_000 секунды).
        long startNanos = System.nanoTime();

        // Статус по умолчанию. Если метод упадёт — изменим на "error".
        String status = "success";

        try {
            // joinPoint.proceed(): вызываем реальный метод (createOrder, getOrder и т.д.).
            // Возвращаем результат вызывающему коду.
            return joinPoint.proceed();

        } catch (Exception e) {
            // Ловим любое исключение, помечаем статус ошибкой.
            // Перебрасываем исключение дальше — не проглатываем!
            status = "error";
            throw e;

        } finally {
            // finally: выполняется ВСЕГДА, даже при исключении.
            // Гарантируем запись метрики в любом случае.

            // Вычисляем прошедшее время: текущее минус стартовое.
            long durationNanos = System.nanoTime() - startNanos;

            // Вызываем запись метрик (Counter + Timer).
            recordMetrics(metric, joinPoint, durationNanos, status);
        }
    }

    // Приватный метод записи метрик. Выделен для читаемости.
    // metric: аннотация с параметрами.
    // joinPoint: доступ к контексту вызова (имя класса).
    // durationNanos: измеренное время.
    // status: "success" или "error".
    private void recordMetrics(
            BusinessMetric metric,
            ProceedingJoinPoint joinPoint,
            long durationNanos,
            String status
    ) {
        // try-catch: метрики НИКОГДА не должны ломать бизнес-логику.
        // Даже если запись метрики упадёт — метод уже отработал.
        try {

            // Получаем имя метрики из аннотации: "orders.created", "orders.retrieved".
            String metricName = metric.value();

            // Получаем имя класса через reflection: "OrderService".
            // joinPoint.getTarget(): проксированный бин (наш сервис).
            // getClass().getSimpleName(): "OrderService" (без package).
            String className = joinPoint.getTarget().getClass().getSimpleName();

            // Tags.of(): создаём неизменяемый набор тегов.
            // "status": динамический тег (success/error).
            // "class": статичный тег (имя класса для фильтрации в Grafana).
            Tags allTags = Tags.of("status", status, "class", className);

            // Добавляем кастомные теги из аннотации: @TimedBusinessMetric(tags = {"type=write"}).
            allTags = addCustomTags(allTags, metric.tags());

            // Counter.builder(): создаём билдер счётчика.
            // metricName + ".total": суффикс по соглашению (Prometheus-style).
            // .tags(allTags): привязываем теги (статус, класс, кастомные).
            // .description(): человекочитаемое описание для документации.
            // .register(meterRegistry): регистрируем в реестре (или получаем существующий).
            // .increment(): увеличиваем счётчик на 1.
            Counter.builder(metricName + ".total")
                    .tags(allTags)
                    .description("Total calls")
                    .register(meterRegistry)
                    .increment();

            // Строим ключ для кэша Timer'ов.
            // Ключ включает ВСЕ параметры, влияющие на уникальность метрики.
            // Почему включаем status: чтобы p95 считался отдельно для success и error.
            String timerKey = buildTimerKey(metricName, className, metric.tags(), status);

            // computeIfAbsent(): атомарная операция "получить или создать".
            // Если ключ есть — возвращаем кэшированный Timer.
            // Если нет — создаём через лямбду, кладём в кэш, возвращаем.
            // Потокобезопасно: ConcurrentHashMap гарантирует единственное создание.
            Timer timer = timerCache.computeIfAbsent(timerKey, key -> {

                        // Создаём теги заново (нельзя reuse allTags — билдер копирует).
                        Tags timerTags = Tags.of("class", className, "status", status);

                        timerTags = addCustomTags(timerTags, metric.tags());

                        // Timer.builder(): создаём билдер таймера.
                        // metricName + ".duration": суффикс по соглашению.
                        // .tags(timerTags): теги для фильтрации в Grafana.
                        // .description(): описание для документации.
                        // .publishPercentileHistogram(): включаем расчёт p50, p95, p99.
                        //   Без этого только count, sum, max — нет перцентилей!
                        // .sla(): закомментировано — пороги для бакетов (будущая фича).
                        // .register(meterRegistry): регистрация в реестре.
                        return Timer.builder(metricName + ".duration")
                                .tags(timerTags)
                                .description("Execution duration")
                                .publishPercentileHistogram()
                                // SLA как "фича на будущее":
                                // .sla(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(500))
                                .register(meterRegistry);
                    }
            );

            // Записываем измеренное время.
            // durationNanos: время в наносекундах.
            // TimeUnit.NANOSECONDS: указываем единицу измерения.
            // Timer конвертирует в базовую единицу (секунды для Prometheus).
            timer.record(durationNanos, TimeUnit.NANOSECONDS);

        } catch (Exception e) {
            // Логируем ошибку метрик, но НЕ прерываем выполнение.
            // metric.value(): имя метрики для диагностики.
            log.warn("Failed to record metrics for {}", metric.value(), e);
        }
    }

    /**
     * Безопасное добавление кастомных тегов (обрабатывает = в значении)
     */
    // Приватный метод парсинга кастомных тегов из аннотации.
    // base: базовые теги (status, class).
    // tagExpressions: массив строк из аннотации: ["type=write", "priority=high"].
    // Возвращает: новый Tags с добавленными тегами.
    private Tags addCustomTags(
            Tags base,
            String[] tagExpressions
    ) {

        // Если кастомных тегов нет — возвращаем базовые.
        if (tagExpressions == null) return base;

        // Начинаем с базовых тегов.
        Tags result = base;

        // Перебираем все выражения тегов.
        for (String tagExpr : tagExpressions) {

            // Ищем первый '=' в строке.
            // Не используем split() — он ломается на "key=value=with=equals".
            int equalsIndex = tagExpr.indexOf('=');

            // Проверяем, что '=' есть и не в начале (ключ не пустой).
            if (equalsIndex > 0) {

                // Подстрока до '=': ключ. trim() убирает пробелы.
                String key = tagExpr.substring(0, equalsIndex).trim();

                // Подстрока после '=': значение (может содержать '=').
                String value = tagExpr.substring(equalsIndex + 1).trim();

                // Tags.and(): создаёт НОВЫЙ неизменяемый Tags с добавленным тегом.
                // Старый Tags не модифицируется (immutable pattern).
                result = result.and(key, value);
            }
        }

        // Возвращаем результат со всеми тегами.
        return result;
    }

    /**
     * Построение ключа для кэша Timer'а
     */
    // Приватный метод построения ключа для кэша Timer'ов.
    // Ключ должен быть уникальным для каждой комбинации параметров.
    // Параметры: имя метрики, имя класса, кастомные теги, статус.
    private String buildTimerKey(
            String metricName,    // "orders.created"
            String className,     // "OrderService"
            String[] customTags,  // ["type=write", "priority=high"]
            String status         // "success" или "error"
    ) {
        // StringBuilder: эффективная конкатенация строк.
        StringBuilder key = new StringBuilder();

        // Добавляем имя метрики и класс.
        key.append(metricName).append('.').append(className);

        // Если есть кастомные теги — добавляем в ключ.
        if (customTags != null && customTags.length > 0) {

            // Клонируем массив — не модифицируем оригинал.
            String[] sorted = customTags.clone();

            // Сортируем для консистентности ключа.
            // ["b=2", "a=1"] и ["a=1", "b=2"] дадут одинаковый ключ.
            Arrays.sort(sorted);

            // Добавляем отсортированные теги в ключ.
            for (String tag : sorted) {
                key.append('.').append(tag);
            }
        }

        // Добавляем статус в конец ключа.
        // Это позволяет иметь отдельные Timer для success и error.
        key.append('.').append(status);

        // Пример результата: "orders.created.OrderService.type=write.priority=high.success"
        return key.toString();
    }
}