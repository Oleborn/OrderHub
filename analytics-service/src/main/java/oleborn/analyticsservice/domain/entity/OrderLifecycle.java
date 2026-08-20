package oleborn.analyticsservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_lifecycle")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLifecycle {

    @Id
    private Long orderId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "payment_started_at")
    private Instant paymentStartedAt;

    @Column(name = "payment_completed_at")
    private Instant paymentCompletedAt;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "notification_sent_at")
    private Instant notificationSentAt;
}