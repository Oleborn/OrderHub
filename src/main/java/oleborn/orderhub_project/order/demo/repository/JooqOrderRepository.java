package oleborn.orderhub_project.order.demo.repository;

import lombok.RequiredArgsConstructor;
import oleborn.assistant_core.jooq.tables.records.OrderItemsRecord;
import oleborn.assistant_core.jooq.tables.records.OrdersRecord;
import oleborn.orderhub_project.order.Order;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static oleborn.assistant_core.jooq.tables.OrderItems.ORDER_ITEMS;
import static oleborn.assistant_core.jooq.tables.Orders.ORDERS;

/**
 * ДЕМОНСТРАЦИОННЫЙ КЛАСС
 * Показывает type-safe подход jOOQ с генерацией кода из схемы БД
 */
@Repository
@RequiredArgsConstructor
public class JooqOrderRepository {

    // DSLContext - основной интерфейс jOOQ для построения запросов
    // Это типобезопасный DSL (Domain Specific Language) для SQL
    private final DSLContext dsl;

    public Order save(Order order) {
        // СОХРАНЕНИЕ ORDER С ИСПОЛЬЗОВАНИЕМ jOOQ RECORD

        // Создаем новый Record для таблицы orders
        // Record - это типобезопасное представление строки таблицы
        OrdersRecord orderRecord = dsl.newRecord(ORDERS);

        // Устанавливаем значение статуса
        // ORDERS.STATUS - это сгенерированное константное поле
        // Преобразуем enum OrderStatus в String
        orderRecord.setStatus(order.getStatus().name());

        // Конвертируем Instant в OffsetDateTime для PostgreSQL
        // PostgreSQL хранит даты с часовым поясом (TIMESTAMP WITH TIME ZONE)
        // ZoneOffset.UTC - используем UTC для консистентности
        OffsetDateTime offsetDateTime = order.getCreateAt().atOffset(ZoneOffset.UTC);

        // Устанавливаем дату создания и номер заказа
        // Все setter'ы типобезопасны - IDE будет подсказывать доступные методы
        orderRecord.setCreateAt(offsetDateTime);
        orderRecord.setOrderNumber(order.getOrderNumber());

        // Сохраняем запись в базу данных
        // Метод store() выполняет INSERT или UPDATE в зависимости от состояния Record
        // После сохранения Record автоматически заполняется сгенерированным ID
        orderRecord.store();  // Сохраняем и получаем сгенерированный ID

        // Получаем сгенерированный ID из сохраненной записи
        Long orderId = orderRecord.getId();

        // Устанавливаем ID в исходный объект Order
        order.setId(orderId);

        // СОХРАНЕНИЕ ORDERITEMS (BATCH INSERT)
        // Проверяем, что в заказе есть позиции
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            // Создаем список Records для позиций заказа
            List<OrderItemsRecord> itemRecords = order.getItems().stream()
                    .map(item -> {
                        // Создаем новый Record для таблицы order_items
                        OrderItemsRecord record = dsl.newRecord(ORDER_ITEMS);

                        // Устанавливаем значения полей
                        // Все поля типобезопасны - компилятор проверит правильность
                        record.setOrderId(orderId);          // Связь с родительским заказом
                        record.setProductId(item.getProductId());   // ID продукта
                        record.setProductName(item.getProductName()); // Название продукта
                        record.setQuantity(item.getQuantity());     // Количество
                        record.setPrice(item.getPrice());           // Цена

                        return record;
                    })
                    .toList();  // Собираем в неизменяемый список

            // Выполняем batch insert всех записей
            // batchInsert оптимизирован для вставки множества строк одним запросом
            dsl.batchInsert(itemRecords).execute();
        }

        // Возвращаем обновленный объект Order с установленным ID
        return order;
    }
}