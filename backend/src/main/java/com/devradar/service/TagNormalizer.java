package com.devradar.service;

import org.springframework.stereotype.Component;

@Component
public class TagNormalizer {
    public String normalize(String input) {
        if (input == null) return "";
        String trimmed = input.trim().toLowerCase();
        return trimmed.replaceAll("[\\s.,\\-+]", "_");
    }
}
