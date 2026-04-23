package com.devradar.ingest.deps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PackageJsonParser implements DependencyFileParser {

    private static final Logger LOG = LoggerFactory.getLogger(PackageJsonParser.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public List<ParsedDependency> parse(String fileContent) {
        List<ParsedDependency> out = new ArrayList<>();
        try {
            JsonNode root = JSON.readTree(fileContent);
            extractDeps(root.get("dependencies"), out);
            extractDeps(root.get("devDependencies"), out);
        } catch (Exception e) {
            LOG.warn("failed to parse package.json: {}", e.toString());
        }
        return out;
    }

    private static void extractDeps(JsonNode node, List<ParsedDependency> out) {
        if (node == null || !node.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String version = entry.getValue().asText();
            if (version.startsWith("workspace:") || version.startsWith("file:")
                    || version.startsWith("link:")) continue;
            out.add(new ParsedDependency("NPM", entry.getKey(), version));
        }
    }
}
