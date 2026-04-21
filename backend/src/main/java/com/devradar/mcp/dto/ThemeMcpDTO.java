package com.devradar.mcp.dto;

import java.util.List;

public record ThemeMcpDTO(String title, String summary, List<CitationMcpDTO> citations) {}
