package oleborn.reactivenotificationservice.model.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("notification_sent")
public class NotificationSent {

    @Id
    private Long id;

    private Long orderId;

    private String status;   // "PAID" или "CANCELLED"

    private Instant sentAt;
}