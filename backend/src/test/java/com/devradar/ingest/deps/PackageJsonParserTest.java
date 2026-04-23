package com.devradar.ingest.deps;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PackageJsonParserTest {

    final PackageJsonParser parser = new PackageJsonParser();

    @Test
    void parse_extractsBothDepsAndDevDeps() {
        String json = """
            {
              "dependencies": {
                "react": "^18.2.0",
                "react-dom": "^18.2.0"
              },
              "devDependencies": {
                "vite": "^5.0.0"
              }
            }
            """;

        List<ParsedDependency> deps = parser.parse(json);

        assertThat(deps).hasSize(3);
        assertThat(deps).allMatch(d -> d.ecosystem().equals("NPM"));
        assertThat(deps.stream().map(ParsedDependency::packageName))
            .containsExactlyInAnyOrder("react", "react-dom", "vite");
    }

    @Test
    void parse_handlesWorkspaceProtocol() {
        String json = """
            {
              "dependencies": {
                "my-lib": "workspace:*",
                "express": "4.18.2"
              }
            }
            """;

        List<ParsedDependency> deps = parser.parse(json);

        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).packageName()).isEqualTo("express");
        assertThat(deps.get(0).version()).isEqualTo("4.18.2");
    }

    @Test
    void parse_returnsEmpty_onMalformedJson() {
        assertThat(parser.parse("not json")).isEmpty();
    }
}
