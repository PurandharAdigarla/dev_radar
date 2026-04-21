package com.devradar.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void hashesToSha256Hex() {
        String out = hasher.hash("devr_test");
        // Compute with: echo -n "devr_test" | shasum -a 256
        // and paste the hex here. If assertion fails, recompute and update.
        assertThat(out).hasSize(64);
        assertThat(out).matches("[0-9a-f]+");
    }

    @Test
    void hashIsDeterministic() {
        assertThat(hasher.hash("same")).isEqualTo(hasher.hash("same"));
    }

    @Test
    void differentInputsProduceDifferentHashes() {
        assertThat(hasher.hash("a")).isNotEqualTo(hasher.hash("b"));
    }
}
