package com.devradar.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator gen = new ApiKeyGenerator();

    @Test
    void generatesKeyWithDevrPrefixAnd32BodyChars() {
        String key = gen.generate();
        assertThat(key).startsWith("devr_");
        assertThat(key.length()).isEqualTo(37); // "devr_" (5) + 32
    }

    @Test
    void generatesDistinctKeys() {
        String a = gen.generate();
        String b = gen.generate();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void prefixReturnsFirst8Characters() {
        String key = "devr_abcdefghijklmnopqrstuvwxyz012345";
        assertThat(gen.prefix(key)).isEqualTo("devr_abc");
    }
}
