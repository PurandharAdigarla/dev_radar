package com.devradar.web.rest;

import com.devradar.domain.User;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.plan.PlanEnforcementService;
import com.devradar.plan.PlanLimits;
import com.devradar.repository.UserRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.PlanInfoDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/plan")
public class PlanResource {

    private final PlanEnforcementService planService;
    private final UserRepository userRepo;

    public PlanResource(PlanEnforcementService planService, UserRepository userRepo) {
        this.planService = planService;
        this.userRepo = userRepo;
    }

    @GetMapping
    public PlanInfoDTO getPlan() {
        Long userId = currentUserId();
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PlanLimits limits = planService.getLimits(userId);
        return new PlanInfoDTO(
            user.getPlan(),
            limits.radarsPerMonth(),
            limits.sourcesPerRadar(),
            limits.maxInterests(),
            limits.teamAccess(),
            limits.emailDigest(),
            planService.isTrialActive(user),
            user.getTrialStartedAt() != null,
            user.getPlanExpiresAt()
        );
    }

    @PostMapping("/start-trial")
    public PlanInfoDTO startTrial() {
        Long userId = currentUserId();
        User user = planService.startTrial(userId);
        PlanLimits limits = PlanLimits.forPlan(user.getPlan());
        return new PlanInfoDTO(
            user.getPlan(),
            limits.radarsPerMonth(),
            limits.sourcesPerRadar(),
            limits.maxInterests(),
            limits.teamAccess(),
            limits.emailDigest(),
            true,
            true,
            user.getPlanExpiresAt()
        );
    }

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }
}
