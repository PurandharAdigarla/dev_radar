package com.devradar.radar.application;

import com.devradar.domain.InterestTag;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.EngagementEventRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.UserDependencyRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserInterestService;
import com.devradar.web.rest.dto.DependencySummaryDTO;
import com.devradar.web.rest.dto.UserStatsDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RadarStatsService {

    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final EngagementEventRepository engagementRepo;
    private final SourceItemRepository sourceItemRepo;
    private final UserDependencyRepository depRepo;
    private final InterestTagRepository tagRepo;
    private final UserInterestService interests;

    public RadarStatsService(RadarRepository radarRepo,
                             RadarThemeRepository themeRepo,
                             EngagementEventRepository engagementRepo,
                             SourceItemRepository sourceItemRepo,
                             UserDependencyRepository depRepo,
                             InterestTagRepository tagRepo,
                             UserInterestService interests) {
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.engagementRepo = engagementRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.depRepo = depRepo;
        this.tagRepo = tagRepo;
        this.interests = interests;
    }

    public UserStatsDTO getUserStats() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        long radarCount = radarRepo.countByUserIdAndStatus(uid, RadarStatus.READY);
        var latestPage = radarRepo.findByUserIdOrderByGeneratedAtDesc(uid, PageRequest.of(0, 1));
        String latestDate = latestPage.getContent().stream()
                .filter(r -> r.getGeneratedAt() != null)
                .findFirst()
                .map(r -> r.getGeneratedAt().toString())
                .orElse(null);

        // Batch query to count themes across all recent radars (fixes N+1)
        List<Radar> recentRadars = radarRepo.findByUserIdOrderByGeneratedAtDesc(uid, PageRequest.of(0, 50)).getContent();
        List<Long> readyRadarIds = recentRadars.stream()
                .filter(r -> r.getStatus() == RadarStatus.READY)
                .map(Radar::getId)
                .toList();

        int themeCount = 0;
        if (!readyRadarIds.isEmpty()) {
            Map<Long, Long> themeCounts = new HashMap<>();
            for (Object[] row : themeRepo.countThemesByRadarIds(readyRadarIds)) {
                themeCounts.put((Long) row[0], (Long) row[1]);
            }
            themeCount = themeCounts.values().stream().mapToInt(Long::intValue).sum();
        }

        long engagementCount = engagementRepo.countByUserId(uid);

        // Count new source items matching user interests since their last radar
        int newItems = 0;
        Instant lastRadarAt = latestPage.getContent().stream()
                .filter(r -> r.getGeneratedAt() != null && r.getStatus() == RadarStatus.READY)
                .findFirst()
                .map(Radar::getGeneratedAt)
                .orElse(null);
        if (lastRadarAt != null) {
            newItems = (int) sourceItemRepo.countNewItemsForUserSince(uid, lastRadarAt);
        }

        return new UserStatsDTO((int) radarCount, themeCount, (int) engagementCount, latestDate, newItems);
    }

    public DependencySummaryDTO getDependencySummary() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        // Placeholder: dependency scanning is not yet implemented
        return new DependencySummaryDTO(0, 0, 0, List.of());
    }

    public List<String> getSuggestedInterests() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        // Detect tags from user's dependency scan ecosystems
        Set<String> detectedSlugs = new LinkedHashSet<>();
        var deps = depRepo.findByUserId(uid);
        for (var dep : deps) {
            List<String> slugs = ECOSYSTEM_TO_SLUGS.get(dep.getEcosystem().toLowerCase());
            if (slugs != null) detectedSlugs.addAll(slugs);
        }

        // Subtract the user's current interests
        Set<String> currentSlugs = interests.findInterestsForUser(uid).stream()
                .map(InterestTag::getSlug)
                .collect(Collectors.toSet());
        detectedSlugs.removeAll(currentSlugs);

        // Filter to only slugs that exist as valid interest tags
        if (detectedSlugs.isEmpty()) return List.of();
        return tagRepo.findBySlugIn(List.copyOf(detectedSlugs)).stream()
                .map(InterestTag::getSlug)
                .toList();
    }

    private static final Map<String, List<String>> ECOSYSTEM_TO_SLUGS = Map.ofEntries(
        Map.entry("maven", List.of("java", "spring_boot")),
        Map.entry("npm", List.of("javascript", "typescript", "react", "frontend")),
        Map.entry("gradle", List.of("java", "kotlin", "spring_boot")),
        Map.entry("pip", List.of("python", "django", "fastapi")),
        Map.entry("cargo", List.of("rust")),
        Map.entry("go", List.of("go")),
        Map.entry("nuget", List.of("csharp")),
        Map.entry("gem", List.of("rails"))
    );
}
