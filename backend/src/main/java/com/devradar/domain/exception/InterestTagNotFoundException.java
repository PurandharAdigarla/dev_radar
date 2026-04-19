package com.devradar.domain.exception;

public class InterestTagNotFoundException extends RuntimeException {
    public InterestTagNotFoundException(String slug) {
        super("Interest tag not found: " + slug);
    }
}
