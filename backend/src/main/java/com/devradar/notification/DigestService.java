package com.devradar.notification;

import com.devradar.domain.*;
import com.devradar.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DigestService {

    private static final Logger LOG = LoggerFactory.getLogger(DigestService.class);

    private final NotificationPreferenceRepository prefRepo;
    private final UserRepository userRepo;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final EmailRenderer renderer;
    private final Optional<EmailSender> emailSender;

    public DigestService(NotificationPreferenceRepository prefRepo, UserRepository userRepo,
                         RadarRepository radarRepo, RadarThemeRepository themeRepo,
                         RadarThemeItemRepository themeItemRepo, SourceItemRepository sourceItemRepo,
                         EmailRenderer renderer, Optional<EmailSender> emailSender) {
        this.prefRepo = prefRepo;
        this.userRepo = userRepo;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.renderer = renderer;
        this.emailSender = emailSender;
    }

    public void sendDigestForUser(Long userId) {
        NotificationPreference pref = prefRepo.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("No notification preferences for user " + userId));
        User user = userRepo.findById(userId).orElseThrow();

        String toAddress = pref.getEmailAddress() != null ? pref.getEmailAddress() : user.getEmail();

        Radar radar = findLatestReadyRadar(userId);
        if (radar == null) {
            LOG.info("No recent READY radar for user={}, skipping digest", userId);
            return;
        }

        List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radar.getId());
        Map<Long, List<SourceItem>> citedItems = loadCitedItems(themes);

        String html = renderer.renderRadarDigest(user.getDisplayName(), themes, citedItems, radar.getId());
        requireSender().send(toAddress, "Dev Radar — Your Weekly Brief", html);
        LOG.info("Digest sent to user={} email={}", userId, toAddress);
    }

    public void sendTestEmail(Long userId) {
        User user = userRepo.findById(userId).orElseThrow();
        NotificationPreference pref = prefRepo.findByUserId(userId).orElse(null);
        String toAddress = (pref != null && pref.getEmailAddress() != null) ? pref.getEmailAddress() : user.getEmail();

        Radar radar = findLatestReadyRadar(userId);
        String html;
        if (radar != null) {
            List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radar.getId());
            Map<Long, List<SourceItem>> citedItems = loadCitedItems(themes);
            html = renderer.renderRadarDigest(user.getDisplayName(), themes, citedItems, radar.getId());
        } else {
            html = renderer.renderTestEmail(user.getDisplayName());
        }

        requireSender().send(toAddress, "Dev Radar — Test Email", html);
        LOG.info("Test email sent to user={} email={}", userId, toAddress);
    }

    Radar findLatestReadyRadar(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, 10));
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        return page.getContent().stream()
            .filter(r -> r.getStatus() == RadarStatus.READY)
            .filter(r -> r.getGeneratedAt() != null && r.getGeneratedAt().isAfter(cutoff))
            .findFirst()
            .orElse(null);
    }

    private Map<Long, List<SourceItem>> loadCitedItems(List<RadarTheme> themes) {
        Map<Long, List<SourceItem>> map = new LinkedHashMap<>();
        for (var theme : themes) {
            var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(theme.getId());
            List<SourceItem> items = new ArrayList<>();
            for (var rti : rtis) {
                sourceItemRepo.findById(rti.getSourceItemId()).ifPresent(items::add);
            }
            map.put(theme.getId(), items);
        }
        return map;
    }

    private EmailSender requireSender() {
        return emailSender.orElseThrow(() -> new IllegalStateException("Email sending is not configured"));
    }
}
