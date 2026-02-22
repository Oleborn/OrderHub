package oleborn.orderhub_project.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import oleborn.orderhub_project.order.OrderStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "order_number", nullable = false, unique = true)
    private String orderNumber;

    @Column(name = "create_at")
    private Instant createAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(List<OrderItem> items) {
        this.status = OrderStatus.CREATED;
        this.createAt = Instant.now();
        this.items.addAll(items);
        this.orderNumber = UUID.randomUUID().toString();

        // Устанавливаем обратную ссылку для каждого OrderItem
        items.forEach(item -> item.setOrder(this));
    }
}
