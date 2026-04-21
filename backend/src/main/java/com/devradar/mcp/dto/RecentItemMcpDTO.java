package com.devradar.mcp.dto;

import java.time.Instant;
import java.util.List;

public record RecentItemMcpDTO(
    String title,
    String url,
    String source,
    Instant postedAt,
    List<String> tags
) {}
