package com.devradar.plan;

import com.devradar.domain.UserPlan;

public record PlanLimits(int radarsPerMonth, int sourcesPerRadar, int maxInterests, boolean teamAccess, boolean emailDigest) {
    public static PlanLimits forPlan(UserPlan plan) {
        return switch (plan) {
            case FREE -> new PlanLimits(5, 3, 10, false, false);
            case PRO -> new PlanLimits(50, 10, 50, false, true);
            case TEAM -> new PlanLimits(100, 20, 100, true, true);
        };
    }
}
