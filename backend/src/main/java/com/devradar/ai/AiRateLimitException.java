package com.devradar.ai;

public class AiRateLimitException extends AiProviderException {
    public AiRateLimitException(String provider, String detail) {
        super(provider + " rate limit exceeded" + (detail.isBlank() ? "" : ": " + detail));
    }
}
