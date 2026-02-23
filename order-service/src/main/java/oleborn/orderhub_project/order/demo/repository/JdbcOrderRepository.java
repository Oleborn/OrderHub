package oleborn.orderhub_project.order.demo.repository;

import lombok.RequiredArgsConstructor;
import oleborn.orderhub_project.order.domain.Order;
import oleborn.orderhub_project.order.domain.OrderItem;
import oleborn.orderhub_project.order.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

// Аннотация @Repository отмечает класс как компонент Spring для работы с данными
// "jdbcOrderRepository" - имя бина, по которому можно инжектить эту реализацию
@Repository("jdbcOrderRepository")
@RequiredArgsConstructor
public class JdbcOrderRepository {

    // JdbcTemplate - основная абстракция Spring для работы с JDBC
    // Инкапсулирует работу с соединениями, обработку исключений, выполнение запросов
    private final JdbcTemplate jdbcTemplate;

    // SQL запрос для вставки записи в таблицу orders
    // Используем text blocks (Java 13+) для читаемости многострочных SQL
    // ? - плейсхолдеры для параметров, защита от SQL-инъекций
    private static final String SET_ORDER = """
            INSERT INTO orders (status, create_at, order_number) 
            VALUES (?, ?, ?)
            """;

    // SQL запрос для вставки записи в таблицу order_items
    private static final String SET_ORDER_ITEMS = """
            INSERT INTO order_items 
            (order_id, product_id, product_name, quantity, price) 
            VALUES (?, ?, ?, ?, ?)
            """;


    // RowMapper - интерфейс для преобразования строки ResultSet в Java-объект
    // Каждая строка результата запроса маппится в объект Order
    private final RowMapper<Order> orderRowMapper = (rs, rowNum) -> {
        // Создаем новый пустой объект Order
        Order order = new Order();

        // Устанавливаем ID из колонки "id" ResultSet
        order.setId(rs.getLong("id"));

        // Преобразуем строку статуса в enum OrderStatus
        order.setStatus(OrderStatus.valueOf(rs.getString("status")));

        // Получаем номер заказа как строку
        order.setOrderNumber(rs.getString("order_number"));

        // Получаем timestamp и конвертируем в Instant
        // PostgreSQL хранит create_at как TIMESTAMP WITH TIME ZONE
        order.setCreateAt(rs.getTimestamp("create_at").toInstant());

        return order;
    };

    // RowMapper для преобразования строки ResultSet в OrderItem
    private final RowMapper<OrderItem> itemRowMapper = (rs, rowNum) -> {
        OrderItem item = new OrderItem();
        item.setId(rs.getLong("id"));
        item.setProductId(rs.getLong("product_id"));
        item.setProductName(rs.getString("product_name"));
        item.setQuantity(rs.getInt("quantity"));
        item.setPrice(rs.getBigDecimal("price"));
        return item;
    };

    // @Transactional гарантирует, что метод выполняется в транзакции
    // Если произойдет исключение - все изменения откатятся
    @Transactional
    public Order save(Order order) {
        // 1. СОХРАНЕНИЕ ORDER

        // KeyHolder - механизм Spring для получения сгенерированных ключей
        // После INSERT запроса позволяет получить автоинкрементный ID
        KeyHolder keyHolder = new GeneratedKeyHolder();

        // jdbcTemplate.update выполняет DML операции (INSERT, UPDATE, DELETE)
        // Принимает PreparedStatementCreator и KeyHolder
        jdbcTemplate.update(
                // PreparedStatementCreator - лямбда для создания PreparedStatement
                connection -> {
                    // Создаем PreparedStatement с флагом RETURN_GENERATED_KEYS
                    // Это указывает драйверу вернуть сгенерированные ключи
                    PreparedStatement ps = connection.prepareStatement(
                            SET_ORDER,
                            Statement.RETURN_GENERATED_KEYS
                    );

                    // Устанавливаем параметры в плейсхолдеры (начинаются с 1)
                    // 1-й параметр: статус заказа как строка (значение enum)
                    ps.setString(1, order.getStatus().name());

                    // 2-й параметр: дата создания как Timestamp
                    // Instant конвертируется в Timestamp для JDBC
                    ps.setTimestamp(2, java.sql.Timestamp.from(order.getCreateAt()));

                    // 3-й параметр: номер заказа (UUID как строка)
                    ps.setString(3, order.getOrderNumber());

                    return ps;
                },
                // KeyHolder будет заполнен сгенерированными ключами
                keyHolder
        );

        // PostgreSQL возвращает всю строку, а не только ID
        // Получаем ID из возвращенной мапы
        Map<String, Object> keys = keyHolder.getKeys();

        Long orderId = null;

        if (keys != null) {
            // PostgreSQL возвращает мапу с колонками
            // Пытаемся получить ID по имени колонки "id"
            if (keys.containsKey("id")) {
                // Приводим Object к Number, затем к Long
                orderId = ((Number) keys.get("id")).longValue();
            }

            // Альтернативный вариант: ищем числовой ID среди всех значений
            // Это fallback на случай, если драйвер вернет данные в другом формате
            for (Object value : keys.values()) {
                if (value instanceof Number) {
                    orderId = ((Number) value).longValue();
                    break;
                }
            }
        }

        // Проверка, что ID был успешно получен
        if (orderId == null) {
            throw new RuntimeException("Failed to get generated order ID");
        }

        // Устанавливаем полученный ID в объект Order
        order.setId(orderId);

        // 2. СОХРАНЕНИЕ ORDERITEMS (BATCH INSERT)
        // Проверяем, что в заказе есть позиции
        if (order.getItems() != null && !order.getItems().isEmpty()) {

            // Делаем orderId effectively final для использования в лямбде
            Long finalOrderId = orderId;

            // Создаем список аргументов для batch-запроса
            // Каждый элемент массива соответствует одному плейсхолдеру в SQL
            List<Object[]> batchArgs = order.getItems().stream()
                    .map(
                            item -> new Object[]{
                                    finalOrderId,        // order_id - связь с родительским заказом
                                    item.getProductId(),  // product_id
                                    item.getProductName(),// product_name
                                    item.getQuantity(),   // quantity
                                    item.getPrice()       // price
                            }
                    )
                    .toList();  // Java 16+ вместо .collect(Collectors.toList())

            // batchUpdate выполняет один SQL для всех записей
            // Эффективнее, чем выполнять отдельные INSERT для каждой позиции
            jdbcTemplate.batchUpdate(SET_ORDER_ITEMS, batchArgs);
        }

        // Возвращаем обновленный объект Order с установленным ID
        return order;
    }
}