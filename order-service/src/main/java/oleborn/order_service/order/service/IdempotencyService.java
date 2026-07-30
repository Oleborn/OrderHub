package oleborn.order_service.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import oleborn.order_service.order.domain.dto.CachedResponse;
import oleborn.order_service.order.domain.dto.OrderResponseDto;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void saveResponse(String key, int status, String body) {

        String value = status + "|" + body;

        redisTemplate.opsForValue().set(key, value, Duration.ofHours(24));
    }

    @SneakyThrows
    public Optional<CachedResponse> getResponse(String key) {

        String value = redisTemplate.opsForValue().get(key);

        log.info("get response from redis: {}", value);

        if (value == null) return Optional.empty();

        String[] parts = value.split("\\|", 2);

        OrderResponseDto orderResponseDto = objectMapper.readValue(parts[1], OrderResponseDto.class);

        return Optional.of(
                CachedResponse.builder()
                        .status(Integer.parseInt(parts[0]))
                        .body(orderResponseDto)
                        .build()
        );
    }
}