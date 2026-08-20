package oleborn.analyticsservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import oleborn.analyticsservice.domain.dto.OrderTimelineDto;
import oleborn.analyticsservice.domain.dto.ProcessingMetricsDto;
import oleborn.analyticsservice.domain.entity.OrderLifecycle;
import oleborn.analyticsservice.repository.OrderLifecycleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsController {

    private final OrderLifecycleRepository repository;

    @GetMapping("/orders/{orderId}/timeline")
    public ResponseEntity<OrderTimelineDto> getTimeline(@PathVariable Long orderId) {
        log.debug("Request timeline for order {}", orderId);
        OrderLifecycle record = repository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        return ResponseEntity.ok(OrderTimelineDto.from(record));
    }

    @GetMapping("/metrics/processing-times")
    public ResponseEntity<ProcessingMetricsDto> getProcessingMetrics() {
        log.debug("Request processing metrics");
        Double avgPayment = repository.getAvgPaymentProcessingTime();
        Double avgNotification = repository.getAvgNotificationTime();
        return ResponseEntity.ok(new ProcessingMetricsDto(avgPayment, avgNotification));
    }
}