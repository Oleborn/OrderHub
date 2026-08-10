package oleborn.order_service.order.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;  
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import oleborn.order_service.order.domain.entity.Order;
import oleborn.order_service.order.exception.NotFoundOrderException;  
import oleborn.order_service.order.repository.OrderRepository;  
import org.springframework.stereotype.Service;  
  
import java.util.concurrent.ConcurrentHashMap;  
import java.util.concurrent.ConcurrentMap;  
  
@Service  
@Slf4j  
public class OrderCacheMapService {  
  
    private final ConcurrentMap<Long, OrderResponseDto> cache = new ConcurrentHashMap<>();  
    private final OrderRepository orderRepository;  
    private final Counter hitCounter;  
    private final Counter missCounter;  
  
    public OrderCacheMapService(OrderRepository orderRepository, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;  
        this.hitCounter = Counter.builder("order.cache.hashmap.hit")  
                .description("HashMap cache hit")  
                .register(meterRegistry);  
        this.missCounter = Counter.builder("order.cache.hashmap.miss")  
                .description("HashMap cache miss")  
                .register(meterRegistry);  
    }  
  
    public OrderResponseDto getOrder(Long id) {  
  
        OrderResponseDto dto = cache.get(id);  
  
        if (dto != null) {
            log.debug("HashMap cache hit for order {}", id);  
            hitCounter.increment();  
            return dto;  
        }  
  
        missCounter.increment();  
  
        log.debug("HashMap cache miss for order {}, loading from DB", id);

        dto = OrderResponseDto.from(
                 orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundOrderException("Order not found: " + id))
         );

        cache.put(id, dto);
  
        return dto;
    }  
}