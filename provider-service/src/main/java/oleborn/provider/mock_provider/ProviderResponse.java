package oleborn.provider.mock_provider;

public record ProviderResponse(
        boolean success,
        String message
) {}