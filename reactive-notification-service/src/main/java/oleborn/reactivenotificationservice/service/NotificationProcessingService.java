package oleborn.reactivenotificationservice.service;

import oleborn.reactivenotificationservice.model.dto.OrderCreatedEvent;
import reactor.core.publisher.Mono;

public interface NotificationProcessingService {

    Mono<Void> processNotification(OrderCreatedEvent event);
}