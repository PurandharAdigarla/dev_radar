package com.devradar.web.rest.dto;

public record TeamDTO(Long id, String name, String slug, String plan, Long ownerId, int memberCount) {}
