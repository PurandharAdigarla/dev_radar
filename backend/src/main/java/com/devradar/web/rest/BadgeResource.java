package com.devradar.web.rest;

import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserInterestRepository;
import com.devradar.domain.Source;
import com.devradar.domain.UserInterest;
import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import com.devradar.security.SecurityUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/badges")
public class BadgeResource {

    private static final String SVG_TEMPLATE = """
        <svg xmlns="http://www.w3.org/2000/svg" width="210" height="20">
          <rect width="80" height="20" rx="3" fill="#555"/>
          <rect x="80" width="130" height="20" rx="3" fill="%s"/>
          <rect width="80" height="20" rx="3" fill="url(#s)"/>
          <rect x="80" width="130" height="20" rx="3" fill="url(#s)"/>
          <linearGradient id="s" x2="0" y2="100%%">
            <stop offset="0" stop-color="#fff" stop-opacity=".1"/>
            <stop offset="1" stop-opacity=".1"/>
          </linearGradient>
          <text x="40" y="14" fill="#fff" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11" text-anchor="middle">Dev Radar</text>
          <text x="145" y="14" fill="#fff" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11" text-anchor="middle">%s</text>
        </svg>
        """;

    private static final String COLOR_GREEN = "#4c1";
    private static final String COLOR_RED = "#e05d44";
    private static final MediaType SVG_MEDIA_TYPE = MediaType.valueOf("image/svg+xml");

    private final UserInterestRepository userInterestRepo;
    private final InterestTagRepository interestTagRepo;
    private final SourceItemRepository sourceItemRepo;
    private final SourceRepository sourceRepo;

    public BadgeResource(UserInterestRepository userInterestRepo,
                         InterestTagRepository interestTagRepo,
                         SourceItemRepository sourceItemRepo,
                         SourceRepository sourceRepo) {
        this.userInterestRepo = userInterestRepo;
        this.interestTagRepo = interestTagRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceRepo = sourceRepo;
    }

    @GetMapping(value = "/me/security.svg", produces = "image/svg+xml")
    public ResponseEntity<String> securityBadge() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).build();
        int criticalCount = countCriticalCves(userId);
        String color = criticalCount > 0 ? COLOR_RED : COLOR_GREEN;
        String label = criticalCount + " critical CVE" + (criticalCount != 1 ? "s" : "");
        String svg = String.format(SVG_TEMPLATE, color, label);

        return ResponseEntity.ok()
                .contentType(SVG_MEDIA_TYPE)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(svg);
    }

    private int countCriticalCves(Long userId) {
        // Find user's interest tag slugs
        List<UserInterest> interests = userInterestRepo.findByUserId(userId);
        if (interests.isEmpty()) return 0;

        List<Long> tagIds = interests.stream().map(UserInterest::getInterestTagId).toList();
        List<InterestTag> tags = interestTagRepo.findAllById(tagIds);
        List<String> slugs = tags.stream().map(InterestTag::getSlug).toList();
        if (slugs.isEmpty()) return 0;

        // Find the GHSA source
        Source ghsaSource = sourceRepo.findByCode("GHSA").orElse(null);
        if (ghsaSource == null) return 0;

        // Count GHSA source items from the last 30 days that match user's interest tags
        // and contain "CRITICAL" in their description
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        var items = sourceItemRepo.findRecentByUserInterests(userId, cutoff, null, 500);

        return (int) items.stream()
                .filter(si -> si.getSourceId().equals(ghsaSource.getId()))
                .filter(si -> si.getDescription() != null
                        && si.getDescription().toUpperCase().contains("CRITICAL"))
                .count();
    }
}
