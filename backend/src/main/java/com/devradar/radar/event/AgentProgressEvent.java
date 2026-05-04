package com.devradar.radar.event;

import java.util.List;

public record AgentProgressEvent(
    Long radarId,
    String agent,
    String phase,
    List<String> searchQueries,
    List<SearchResult> searchResults
) {
    public record SearchResult(String title, String domain, String url) {}
}
