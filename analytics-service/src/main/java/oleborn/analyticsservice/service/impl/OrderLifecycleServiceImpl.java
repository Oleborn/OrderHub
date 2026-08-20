package oleborn.analyticsservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.analyticsservice.repository.OrderLifecycleRepository;
import oleborn.analyticsservice.service.OrderLifecycleService;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderLifecycleServiceImpl implements OrderLifecycleService {

    private final OrderLifecycleRepository repository;

    public void upsertCreatedAt(Long orderId, Instant createdAt) {
        repository.upsertCreatedAt(orderId, createdAt);
        log.debug("Upserted created_at for order {}", orderId);
    }

    public void upsertPaymentStartedAt(Long orderId, Instant startedAt) {
        repository.upsertPaymentStartedAt(orderId, startedAt);
        log.debug("Upserted payment_started_at for order {}", orderId);
    }

    public void upsertPaymentCompletedAt(Long orderId, Instant completedAt, String status) {
        repository.upsertPaymentCompletedAt(orderId, completedAt, status);
        log.debug("Upserted payment_completed_at for order {}", orderId);
    }

    public void upsertNotificationSentAt(Long orderId, Instant sentAt) {
        repository.upsertNotificationSentAt(orderId, sentAt);
        log.debug("Upserted notification_sent_at for order {}", orderId);
    }

}
