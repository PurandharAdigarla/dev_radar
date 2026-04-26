package com.devradar.plan;

import com.devradar.domain.User;
import com.devradar.domain.UserPlan;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class PlanEnforcementService {

    private final UserRepository userRepo;
    private final RadarRepository radarRepo;

    public PlanEnforcementService(UserRepository userRepo, RadarRepository radarRepo) {
        this.userRepo = userRepo;
        this.radarRepo = radarRepo;
    }

    public void checkRadarLimit(Long userId) {
        User user = getUser(userId);
        checkAndDowngradeExpiredTrial(user);
        PlanLimits limits = PlanLimits.forPlan(user.getPlan());
        long count = radarRepo.countByUserIdAndStatus(userId, com.devradar.domain.RadarStatus.READY);
        if (count >= limits.radarsPerMonth()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Monthly radar limit reached (" + limits.radarsPerMonth() + "). Upgrade your plan for more.");
        }
    }

    public void checkTeamAccess(Long userId) {
        User user = getUser(userId);
        checkAndDowngradeExpiredTrial(user);
        PlanLimits limits = PlanLimits.forPlan(user.getPlan());
        if (!limits.teamAccess()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Team features require a TEAM plan. Upgrade to access team features.");
        }
    }

    public PlanLimits getLimits(Long userId) {
        User user = getUser(userId);
        checkAndDowngradeExpiredTrial(user);
        return PlanLimits.forPlan(user.getPlan());
    }

    public User startTrial(Long userId) {
        User user = getUser(userId);
        if (user.getTrialStartedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trial already used.");
        }
        if (user.getPlan() != UserPlan.FREE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already on a paid plan.");
        }
        user.setPlan(UserPlan.PRO);
        user.setTrialStartedAt(Instant.now());
        user.setPlanExpiresAt(Instant.now().plus(14, ChronoUnit.DAYS));
        return userRepo.save(user);
    }

    public boolean isTrialActive(User user) {
        return user.getTrialStartedAt() != null
            && user.getPlanExpiresAt() != null
            && user.getPlanExpiresAt().isAfter(Instant.now());
    }

    private void checkAndDowngradeExpiredTrial(User user) {
        if (user.getPlan() != UserPlan.FREE
            && user.getPlanExpiresAt() != null
            && user.getPlanExpiresAt().isBefore(Instant.now())) {
            user.setPlan(UserPlan.FREE);
            userRepo.save(user);
        }
    }

    private User getUser(Long userId) {
        return userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
