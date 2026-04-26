package com.devradar.web.rest.dto;

import java.time.Instant;

public record TeamMemberDTO(Long userId, String role, Instant joinedAt) {}
