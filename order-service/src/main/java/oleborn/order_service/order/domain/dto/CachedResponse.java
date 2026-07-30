package oleborn.order_service.order.domain.dto;

import lombok.Builder;

@Builder
public record CachedResponse(int status, String body) {}