package com.ntccpay.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Per-merchant API keys. Values come from the environment (never committed for
 * real merchants). Map is merchant-id -> api-key; the filter does the reverse lookup.
 */
@ConfigurationProperties(prefix = "ntccpay.security")
public record ApiKeyProperties(Map<String, String> apiKeys) {

    public ApiKeyProperties {
        apiKeys = apiKeys == null ? Map.of() : Map.copyOf(apiKeys);
    }

    public String merchantFor(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        return apiKeys.entrySet().stream()
                .filter(entry -> entry.getValue().equals(apiKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
