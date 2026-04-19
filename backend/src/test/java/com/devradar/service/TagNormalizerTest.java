package com.devradar.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TagNormalizerTest {

    private final TagNormalizer normalizer = new TagNormalizer();

    @Test
    void replaceSpacesAndPunctuationWithUnderscore() {
        assertThat(normalizer.normalize("Spring Boot")).isEqualTo("spring_boot");
        assertThat(normalizer.normalize("React.js")).isEqualTo("react_js");
        assertThat(normalizer.normalize("C++")).isEqualTo("c__");
    }

    @Test
    void caseInsensitiveLowercased() {
        assertThat(normalizer.normalize("REACT")).isEqualTo("react");
        assertThat(normalizer.normalize("React")).isEqualTo("react");
    }

    @Test
    void handlesDotsAndCommasAndHyphens() {
        assertThat(normalizer.normalize("next-js")).isEqualTo("next_js");
        assertThat(normalizer.normalize("a,b,c")).isEqualTo("a_b_c");
        assertThat(normalizer.normalize(".net")).isEqualTo("_net");
    }

    @Test
    void trimsWhitespace() {
        assertThat(normalizer.normalize("  java  ")).isEqualTo("java");
    }

    @Test
    void emptyOrNullReturnsEmpty() {
        assertThat(normalizer.normalize("")).isEqualTo("");
        assertThat(normalizer.normalize(null)).isEqualTo("");
    }
}
