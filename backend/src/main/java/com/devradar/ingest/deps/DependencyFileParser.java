package com.devradar.ingest.deps;

import java.util.List;

public interface DependencyFileParser {
    List<ParsedDependency> parse(String fileContent);
}
