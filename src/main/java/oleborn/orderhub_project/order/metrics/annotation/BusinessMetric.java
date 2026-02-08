package oleborn.orderhub_project.order.metrics.annotation;

import java.lang.annotation.*;

/**
 * Аннотация для автоматического сбора бизнес-метрик методов сервиса.
 *
 * <p>Применяется к методам, выполняющим бизнес-операции. Аспект {@link BusinessMetricsAspect}
 * перехватывает вызовы помеченных методов и автоматически записывает:
 * <ul>
 *   <li><b>Counter</b> — количество вызовов с разбивкой по статусу (success/error)</li>
 *   <li><b>Timer</b> — время выполнения с перцентилями (p50, p95, p99)</li>
 * </ul>
 *
 * <p>Метрики доступны в endpoints:
 * <ul>
 *   <li>{@code /actuator/metrics/{metricName}.total} — счётчик вызовов</li>
 *   <li>{@code /actuator/metrics/{metricName}.duration} — время выполнения</li>
 *   <li>{@code /actuator/prometheus} — Prometheus-формат для Grafana</li>
 * </ul>
 *
 * <h3>Базовое использование</h3>
 *
 * <p>Простейший случай — только имя метрики:
 * <pre>
 * &#64;TimedBusinessMetric("orders.created")
 * public Order createOrder(CreateOrderRequest request) {
 *     // бизнес-логика
 * }
 * </pre>
 *
 * <p>Создаст метрики:
 * <ul>
 *   <li>{@code orders.created.total} — счётчик вызовов</li>
 *   <li>{@code orders.created.duration} — время выполнения</li>
 * </ul>
 *
 * <h3>Использование с кастомными тегами</h3>
 *
 * <p>Теги добавляют измерения для фильтрации в Grafana. Формат: {@code "ключ=значение"}.
 *
 * <pre>
 * &#64;TimedBusinessMetric(
 *     value = "orders.created",
 *     tags = {"type=write", "priority=high", "source=web"}
 * )
 * public Order createOrder(CreateOrderRequest request) {
 *     // бизнес-логика
 * }
 * </pre>
 *
 * <p>В Prometheus:
 * <pre>
 * orders_created_total{status="success",class="OrderService",type="write",priority="high",source="web"} 15
 * orders_created_duration_seconds{status="success",class="OrderService",type="write",quantile="0.95"} 0.045
 * </pre>
 *
 * <h3>Разные операции одного домена</h3>
 *
 * <p>Используйте общий префикс для группировки метрик одной сущности:
 *
 * <pre>
 * // Создание заказа
 * &#64;TimedBusinessMetric(value = "orders.created", tags = {"operation=create"})
 * public Order createOrder(...) { }
 *
 * // Получение заказа
 * &#64;TimedBusinessMetric(value = "orders.retrieved", tags = {"operation=get"})
 * public Order getOrder(...) { }
 *
 * //В БУДУЩЕМ:
 *
 * // Отмена заказа
 * &#64;TimedBusinessMetric(value = "orders.cancelled", tags = {"operation=cancel"})
 * public void cancelOrder(...) { }
 *
 * // Обновление заказа
 * &#64;TimedBusinessMetric(value = "orders.updated", tags = {"operation=update"})
 * public Order updateOrder(...) { }
 * </pre>
 *
 * <p>В Grafana можно построить единый дашборд "Orders" с фильтрацией по {@code operation}.
 *
 * <h3>Разделение по типу операции (read/write)</h3>
 *
 * <pre>
 * // Операции записи
 * &#64;TimedBusinessMetric(value = "orders.created", tags = {"type=write"})
 * &#64;TimedBusinessMetric(value = "orders.updated", tags = {"type=write"})
 * &#64;TimedBusinessMetric(value = "orders.cancelled", tags = {"type=write"})
 *
 * // Операции чтения
 * &#64;TimedBusinessMetric(value = "orders.retrieved", tags = {"type=read"})
 * &#64;TimedBusinessMetric(value = "orders.listed", tags = {"type=read"})
 * </pre>
 *
 * <p>Позволяет сравнивать нагрузку: write vs read.
 *
 * <h3>Метрики для разных доменов</h3>
 *
 * <p>Не ограничивайтесь заказами — применяйте к любым бизнес-операциям:
 *
 * <pre>
 * // Платежи
 * &#64;TimedBusinessMetric("payments.processed")
 * public Payment processPayment(...) { }
 *
 * // Уведомления
 * &#64;TimedBusinessMetric("notifications.sent")
 * public void sendNotification(...) { }
 *
 * // Интеграции
 * &#64;TimedBusinessMetric("integration.crm.sync")
 * public void syncWithCrm(...) { }
 *
 * // Отчёты
 * &#64;TimedBusinessMetric("reports.generated")
 * public Report generateReport(...) { }
 * </pre>
 *
 * <h3>Соглашения об именовании</h3>
 *
 * <table border="1">
 *   <tr><th>Плохо</th><th>Хорошо</th><th>Почему</th></tr>
 *   <tr>
 *     <td>{@code "createOrder"}</td>
 *     <td>{@code "orders.created"}</td>
 *     <td>Домен.действие — группировка в Grafana</td>
 *   </tr>
 *   <tr>
 *     <td>{@code "getOrder"}</td>
 *     <td>{@code "orders.retrieved"}</td>
 *     <td>Глагол в прошедшем времени (событие произошло)</td>
 *   </tr>
 *   <tr>
 *     <td>{@code "orderCount"}</td>
 *     <td>{@code "orders.created.total"}</td>
 *     <td>Суффикс .total добавляется автоматически для Counter</td>
 *   </tr>
 *   <tr>
 *     <td>{@code "process_time"}</td>
 *     <td>{@code "orders.created.duration"}</td>
 *     <td>Суффикс .duration добавляется автоматически для Timer</td>
 *   </tr>
 * </table>
 *
 * <h3>Автоматические теги (добавляются аспектом)</h3>
 *
 * <p>Помимо указанных в аннотации, аспект всегда добавляет:
 * <ul>
 *   <li>{@code status} — "success" или "error" (динамический)</li>
 *   <li>{@code class} — имя класса сервиса, например "OrderService"</li>
 * </ul>
 *
 * <h3>Ограничения</h3>
 *
 * <ul>
 *   <li>Аннотация работает только на public методах бинов Spring</li>
 *   <li>Внутренние вызовы (this.method()) не перехватываются — используйте self-инъекцию</li>
 *   <li>Значения тегов не должны содержать неограниченное множество значений (cardinality explosion)</li>
 * </ul>
 *
 * <h3>Пример полной метрики в Prometheus</h3>
 *
 * <pre>
 * # Счётчик
 * orders_created_total{status="success",class="OrderService",type="write",priority="high"} 42
 * orders_created_total{status="error",class="OrderService",type="write",priority="high"} 3
 *
 * # Время выполнения (перцентили)
 * orders_created_duration_seconds{status="success",class="OrderService",type="write",quantile="0.5"} 0.045
 * orders_created_duration_seconds{status="success",class="OrderService",type="write",quantile="0.95"} 0.089
 * orders_created_duration_seconds{status="success",class="OrderService",type="write",quantile="0.99"} 0.156
 * </pre>
 *
 * @see oleborn.orderhub_project.order.metrics.aspect.BusinessMetricsAspect
 * @see io.micrometer.core.instrument.Counter
 * @see io.micrometer.core.instrument.Timer
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BusinessMetric {

    /**
     * Имя метрики в формате "домен.действие".
     *
     * <p>Из этого имени автоматически формируются:
     * <ul>
     *   <li>{@code {value}.total} — имя Counter (счётчик вызовов)</li>
     *   <li>{@code {value}.duration} — имя Timer (время выполнения)</li>
     * </ul>
     *
     * <p>Примеры корректных имён:
     * <ul>
     *   <li>{@code "orders.created"}</li>
     *   <li>{@code "orders.retrieved"}</li>
     *   <li>{@code "payments.processed"}</li>
     *   <li>{@code "notifications.sent"}</li>
     * </ul>
     *
     * @return имя метрики
     */
    String value();

    /**
     * Дополнительные теги для метрики в формате "ключ=значение".
     *
     * <p>Теги позволяют фильтровать и группировать метрики в системах мониторинга.
     * Каждый тег добавляет измерение к метрике.
     *
     * <p>Примеры использования:
     * <pre>
     * tags = {"type=write"}                    // тип операции
     * tags = {"type=read", "cache=hit"}        // несколько тегов
     * tags = {"priority=high", "source=mobile"} // приоритет и источник
     * </pre>
     *
     * <p><b>Важно:</b> избегайте тегов с неограниченным множеством значений
     * (userId, orderId, timestamp) — это приводит к cardinality explosion.
     *
     * <p>К автоматическим тегам {@code status} и {@code class} добавляются
     * указанные здесь теги.
     *
     * @return массив тегов в формате "ключ=значение"
     * @see #value()
     */
    String[] tags() default {};
}