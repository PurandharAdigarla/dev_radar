package com.devradar.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RadarOutputParser {

    private static final Logger LOG = LoggerFactory.getLogger(RadarOutputParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public static RadarOutput parse(Map<String, Object> sessionState) {
        List<ThemeOutput> themes = new ArrayList<>();
        int displayOrder = 0;

        // Parse themes from individual result_N keys (research agents)
        for (Map.Entry<String, Object> entry : sessionState.entrySet()) {
            if (entry.getKey().startsWith("result_")) {
                Map<String, Object> resultMap = toMap(entry.getValue());
                if (resultMap == null) continue;
                Object hasNews = resultMap.get("has_news");
                if (hasNews != null && !Boolean.TRUE.equals(hasNews) && !"true".equals(hasNews.toString())) {
                    continue;
                }
                String topic = getString(resultMap, "topic");
                String title = getString(resultMap, "title");
                String summary = getString(resultMap, "summary");
                if (title.isBlank() || summary.isBlank()) continue;

                List<SourceOutput> sources = new ArrayList<>();
                Object sourcesObj = resultMap.get("sources");
                if (sourcesObj instanceof List<?> sourcesList) {
                    for (Object s : sourcesList) {
                        if (s instanceof Map<?, ?> sourceMap) {
                            sources.add(new SourceOutput(
                                getString((Map<String, Object>) sourceMap, "url"),
                                getString((Map<String, Object>) sourceMap, "title")
                            ));
                        }
                    }
                }
                themes.add(new ThemeOutput(topic, title, summary, displayOrder++, sources));
            }
        }

        // Also try radar_output key (from synthesizer if present)
        Object radarOutput = sessionState.get("radar_output");
        Map<String, Object> outputMap = toMap(radarOutput);
        if (outputMap != null && themes.isEmpty()) {
            Object themesObj = outputMap.get("themes");
            if (themesObj instanceof List<?> themesList) {
                for (Object item : themesList) {
                    if (item instanceof Map<?, ?> themeMap) {
                        themes.add(parseTheme((Map<String, Object>) themeMap));
                    }
                }
            }
        }

        List<RepoOutput> repos = new ArrayList<>();
        for (Map.Entry<String, Object> entry : sessionState.entrySet()) {
            if (entry.getKey().startsWith("repos_")) {
                Map<String, Object> repoResult = toMap(entry.getValue());
                if (repoResult == null) continue;
                String topic = repoResult.get("topic") != null ? repoResult.get("topic").toString() : "";
                Object reposObj = repoResult.get("repos");
                if (reposObj instanceof List<?> reposList) {
                    int order = 0;
                    for (Object r : reposList) {
                        if (r instanceof Map<?, ?> repoMap) {
                            repos.add(parseRepo((Map<String, Object>) repoMap, topic, order++));
                        }
                    }
                }
            }
        }

        return new RadarOutput(themes, repos);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (value instanceof String str) {
            String json = extractJson(str);
            if (json != null) {
                try {
                    return MAPPER.readValue(json, new TypeReference<>() {});
                } catch (Exception e) {
                    LOG.warn("Failed to parse JSON from session state value: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '{') depth++;
            else if (text.charAt(i) == '}') depth--;
            if (depth == 0) return text.substring(start, i + 1);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static ThemeOutput parseTheme(Map<String, Object> map) {
        String topic = getString(map, "topic");
        String title = getString(map, "title");
        String summary = getString(map, "summary");
        int displayOrder = getInt(map, "display_order");

        List<SourceOutput> sources = new ArrayList<>();
        Object sourcesObj = map.get("sources");
        if (sourcesObj instanceof List<?> sourcesList) {
            for (Object s : sourcesList) {
                if (s instanceof Map<?, ?> sourceMap) {
                    sources.add(new SourceOutput(
                        getString((Map<String, Object>) sourceMap, "url"),
                        getString((Map<String, Object>) sourceMap, "title")
                    ));
                }
            }
        }

        return new ThemeOutput(topic, title, summary, displayOrder, sources);
    }

    private static RepoOutput parseRepo(Map<String, Object> map, String topic, int order) {
        return new RepoOutput(
            topic,
            getString(map, "repo_url"),
            getString(map, "repo_name"),
            getString(map, "description"),
            getString(map, "why_notable"),
            getString(map, "category"),
            order
        );
    }

    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    public record RadarOutput(List<ThemeOutput> themes, List<RepoOutput> repos) {}
    public record ThemeOutput(String topic, String title, String summary, int displayOrder,
                              List<SourceOutput> sources) {}
    public record SourceOutput(String url, String title) {}
    public record RepoOutput(String topic, String repoUrl, String repoName, String description,
                             String whyNotable, String category, int displayOrder) {}
}
