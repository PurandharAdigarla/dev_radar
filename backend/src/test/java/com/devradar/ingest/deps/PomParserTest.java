package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PomParserTest {

    final PomParser parser = new PomParser();

    @Test
    void parse_extractsDependencies() {
        String pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <dependencies>
                    <dependency>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.16.1</version>
                    </dependency>
                    <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

        List<ParsedDependency> deps = parser.parse(pom);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).ecosystem()).isEqualTo("MAVEN");
        assertThat(deps.get(0).packageName()).isEqualTo("com.fasterxml.jackson.core:jackson-databind");
        assertThat(deps.get(0).version()).isEqualTo("2.16.1");
    }

    @Test
    void parse_skipsPropertyPlaceholders() {
        String pom = """
            <project>
                <dependencies>
                    <dependency>
                        <groupId>io.jsonwebtoken</groupId>
                        <artifactId>jjwt-api</artifactId>
                        <version>${jjwt.version}</version>
                    </dependency>
                </dependencies>
            </project>
            """;

        assertThat(parser.parse(pom)).isEmpty();
    }

    @Test
    void parse_returnsEmpty_onMalformedXml() {
        assertThat(parser.parse("not xml")).isEmpty();
    }
}
