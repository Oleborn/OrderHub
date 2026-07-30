package oleborn.order_service.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import oleborn.order_service.order.domain.dto.CachedResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor

public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void saveResponse(String key, int status, Object body) {

        String value = status + "|" + objectMapper.writeValueAsString(body);

        redisTemplate.opsForValue().set(key, value, Duration.ofHours(24));
    }

    public Optional<CachedResponse> getResponse(String key) {

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) return Optional.empty();

        String[] parts = value.split("\\|", 2);

        return Optional.of(
                CachedResponse.builder()
                        .status(Integer.parseInt(parts[0]))
                        .body(parts[1])
                        .build()
        );
    }
}