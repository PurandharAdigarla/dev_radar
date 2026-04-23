package com.devradar.ingest.deps;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleParser implements DependencyFileParser {

    private static final Pattern DEP_PATTERN = Pattern.compile(
        "(?:implementation|api|compileOnly|runtimeOnly|testImplementation|testRuntimeOnly)" +
        "\\s+['\"]([^:]+):([^:]+):([^'\"$]+)['\"]"
    );

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        Matcher m = DEP_PATTERN.matcher(fileContent);
        while (m.find()) {
            String group = m.group(1);
            String artifact = m.group(2);
            String version = m.group(3).trim();
            if (version.isEmpty()) continue;
            out.add(new ParsedDependency("GRADLE", group + ":" + artifact, version));
        }
        return out;
    }
}
