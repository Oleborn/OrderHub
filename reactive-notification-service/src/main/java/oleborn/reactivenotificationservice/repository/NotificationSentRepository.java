package oleborn.reactivenotificationservice.repository;

import oleborn.reactivenotificationservice.model.entity.NotificationSent;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface NotificationSentRepository extends ReactiveCrudRepository<NotificationSent, Long> {
    Mono<NotificationSent> findByOrderId(Long orderId);
}