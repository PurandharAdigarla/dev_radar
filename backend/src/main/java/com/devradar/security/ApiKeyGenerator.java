package com.devradar.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class ApiKeyGenerator {

    public static final String PREFIX = "devr_";
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int BODY_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + BODY_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < BODY_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public String prefix(String rawKey) {
        if (rawKey == null || rawKey.length() < 8) return rawKey;
        return rawKey.substring(0, 8);
    }
}
