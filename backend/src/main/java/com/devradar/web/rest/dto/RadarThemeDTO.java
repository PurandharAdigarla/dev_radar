package com.devradar.web.rest.dto;

import java.util.List;

public record RadarThemeDTO(Long id, String title, String summary, int displayOrder, List<RadarItemDTO> items) {}
