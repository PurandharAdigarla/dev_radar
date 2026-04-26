package com.devradar.radar.application;

import com.devradar.domain.InterestTag;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.EngagementEventRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserInterestService;
import com.devradar.web.rest.dto.DependencySummaryDTO;
import com.devradar.web.rest.dto.UserStatsDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RadarStatsService {

    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final EngagementEventRepository engagementRepo;
    private final UserInterestService interests;

    public RadarStatsService(RadarRepository radarRepo,
                             RadarThemeRepository themeRepo,
                             EngagementEventRepository engagementRepo,
                             UserInterestService interests) {
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.engagementRepo = engagementRepo;
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

        return new UserStatsDTO((int) radarCount, themeCount, (int) engagementCount, latestDate);
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
        // Placeholder: returns existing user interest slugs as suggestions
        return interests.findInterestsForUser(uid).stream()
                .map(InterestTag::getSlug)
                .toList();
    }
}
