package com.devradar.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class EncryptionConfig {

    @Bean(name = "githubTokenKey")
    public SecretKey githubTokenKey(@Value("${encryption.github-token-key-base64}") String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("github-token-key-base64 must decode to exactly 32 bytes (AES-256). Got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
