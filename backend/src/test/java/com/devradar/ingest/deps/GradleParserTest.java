package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GradleParserTest {

    final GradleParser parser = new GradleParser();

    @Test
    void parse_extractsSingleQuotedDeps() {
        String gradle = """
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter-web:3.5.0'
                testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
            }
            """;

        List<ParsedDependency> deps = parser.parse(gradle);

        assertThat(deps).hasSize(2);
        assertThat(deps.get(0).ecosystem()).isEqualTo("GRADLE");
        assertThat(deps.get(0).packageName()).isEqualTo("org.springframework.boot:spring-boot-starter-web");
        assertThat(deps.get(0).version()).isEqualTo("3.5.0");
    }

    @Test
    void parse_extractsDoubleQuotedDeps() {
        String gradle = """
            dependencies {
                implementation "io.micrometer:micrometer-core:1.12.0"
            }
            """;

        List<ParsedDependency> deps = parser.parse(gradle);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).packageName()).isEqualTo("io.micrometer:micrometer-core");
    }

    @Test
    void parse_skipsVersionlessAndVariableRefs() {
        String gradle = """
            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter'
                implementation "io.jsonwebtoken:jjwt-api:$jjwtVersion"
            }
            """;

        assertThat(parser.parse(gradle)).isEmpty();
    }
}
