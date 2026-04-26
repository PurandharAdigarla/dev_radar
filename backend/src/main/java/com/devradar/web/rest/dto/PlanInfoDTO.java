package com.devradar.web.rest.dto;

import com.devradar.domain.UserPlan;
import java.time.Instant;

public record PlanInfoDTO(
    UserPlan plan,
    int radarsPerMonth,
    int sourcesPerRadar,
    int maxInterests,
    boolean teamAccess,
    boolean emailDigest,
    boolean trialActive,
    boolean trialUsed,
    Instant planExpiresAt
) {}
