package com.devradar.web.rest;

import com.devradar.domain.NotificationPreference;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.notification.DigestService;
import com.devradar.repository.NotificationPreferenceRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.NotificationPreferenceDTO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users/me/notifications")
public class NotificationResource {

    private final NotificationPreferenceRepository prefRepo;
    private final DigestService digestService;

    public NotificationResource(NotificationPreferenceRepository prefRepo, DigestService digestService) {
        this.prefRepo = prefRepo;
        this.digestService = digestService;
    }

    @GetMapping
    public NotificationPreferenceDTO get() {
        Long userId = requireUserId();
        NotificationPreference pref = prefRepo.findByUserId(userId).orElseGet(() -> createDefault(userId));
        return toDto(pref);
    }

    @PutMapping
    public NotificationPreferenceDTO update(@Valid @RequestBody NotificationPreferenceDTO dto) {
        Long userId = requireUserId();
        NotificationPreference pref = prefRepo.findByUserId(userId).orElseGet(() -> createDefault(userId));
        pref.setEmailEnabled(dto.emailEnabled());
        pref.setEmailAddress(dto.emailAddress());
        pref.setDigestDayOfWeek(dto.digestDayOfWeek());
        pref.setDigestHourUtc(dto.digestHourUtc());
        pref = prefRepo.save(pref);
        return toDto(pref);
    }

    @PostMapping("/test-email")
    public ResponseEntity<Void> testEmail() {
        Long userId = requireUserId();
        try {
            digestService.sendTestEmail(userId);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Email delivery is not configured on this instance.");
        }
        return ResponseEntity.ok().build();
    }

    private NotificationPreference createDefault(Long userId) {
        NotificationPreference pref = new NotificationPreference();
        pref.setUserId(userId);
        return prefRepo.save(pref);
    }

    private static NotificationPreferenceDTO toDto(NotificationPreference pref) {
        return new NotificationPreferenceDTO(
            pref.isEmailEnabled(),
            pref.getEmailAddress(),
            pref.getDigestDayOfWeek(),
            pref.getDigestHourUtc()
        );
    }

    private static Long requireUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }
}
