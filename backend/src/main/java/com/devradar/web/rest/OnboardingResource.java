package com.devradar.web.rest;

import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserInterestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingResource {

    private final UserInterestService interestService;

    public OnboardingResource(UserInterestService interestService) {
        this.interestService = interestService;
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, String>> apply(@RequestBody ApplyRequest request) {
        Long userId = currentUserId();
        interestService.setInterestsForUser(userId, request.tagSlugs());
        return ResponseEntity.ok(Map.of("status", "ok", "count", String.valueOf(request.tagSlugs().size())));
    }

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }

    public record ApplyRequest(List<String> tagSlugs) {}
}
