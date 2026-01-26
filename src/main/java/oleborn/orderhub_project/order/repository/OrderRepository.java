package oleborn.orderhub_project.order.repository;

import oleborn.orderhub_project.order.Order;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     *  Загрузка заказа вместе с его позициями
     * Решает классическую проблему N+1 запросов в JPA/Hibernate
     *
     * Без @EntityGraph:
     * 1. SELECT * FROM orders WHERE id = ?  ← 1 запрос за заказом
     * 2. SELECT * FROM order_items WHERE order_id = ?  ← N запросов за позициями
     *
     * С @EntityGraph:
     * 1. SELECT o.*, i.* FROM orders o
     *    LEFT JOIN order_items i ON o.id = i.order_id
     *    WHERE o.id = ?  ← ТОЛЬКО 1 запрос
     */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findWithItemsById(Long id);
}