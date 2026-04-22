package com.devradar.radar.application;

import com.devradar.ingest.job.GitHubStarsReleaseFetcher;
import com.devradar.domain.InterestTag;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.mcp.dto.CitationMcpDTO;
import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.mcp.dto.ThemeMcpDTO;
import com.devradar.repository.*;
import com.devradar.security.SecurityUtils;
import com.devradar.radar.RadarGenerationService;
import com.devradar.radar.RadarService;
import com.devradar.service.UserInterestService;
import com.devradar.web.rest.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RadarApplicationService {

    private final RadarService radarService;
    private final RadarGenerationService generation;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final SourceRepository sourceRepo;
    private final UserInterestService interests;
    private final GitHubStarsReleaseFetcher starsFetcher;

    private final Map<Long, String> sourceNameCache = new ConcurrentHashMap<>();

    @PersistenceContext private EntityManager em;

    public RadarApplicationService(
        RadarService radarService,
        RadarGenerationService generation,
        RadarRepository radarRepo,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        SourceItemRepository sourceItemRepo,
        SourceRepository sourceRepo,
        UserInterestService interests,
        GitHubStarsReleaseFetcher starsFetcher
    ) {
        this.radarService = radarService;
        this.generation = generation;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceRepo = sourceRepo;
        this.interests = interests;
        this.starsFetcher = starsFetcher;
    }

    public RadarSummaryDTO createForCurrentUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        starsFetcher.tryFetchForUser(uid);

        List<InterestTag> userTags = interests.findInterestsForUser(uid);
        List<String> slugs = userTags.stream().map(InterestTag::getSlug).toList();
        List<Long> candidateIds = preFilterCandidates(slugs);

        Radar created = radarService.createPending(uid);
        generation.runGeneration(created.getId(), uid, slugs, candidateIds);
        return summary(created);
    }

    public RadarDetailDTO get(Long radarId) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        Radar r = radarRepo.findById(radarId).orElseThrow();
        if (!r.getUserId().equals(uid)) throw new RuntimeException("forbidden");

        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radarId);
        List<RadarThemeDTO> themeDtos = new ArrayList<>();
        for (var t : themes) {
            var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(t.getId());
            List<RadarItemDTO> itemDtos = new ArrayList<>();
            for (var rti : rtis) {
                SourceItem si = sourceItemRepo.findById(rti.getSourceItemId()).orElse(null);
                if (si == null) continue;
                itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getDescription(), si.getUrl(), si.getAuthor(), resolveSourceName(si.getSourceId())));
            }
            themeDtos.add(new RadarThemeDTO(t.getId(), t.getTitle(), t.getSummary(), t.getDisplayOrder(), itemDtos));
        }
        return new RadarDetailDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount(), themeDtos);
    }

    public Page<RadarSummaryDTO> list(Pageable pageable) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return radarRepo.findByUserIdOrderByGeneratedAtDesc(uid, pageable).map(this::summary);
    }

    private RadarSummaryDTO summary(Radar r) {
        return new RadarSummaryDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount());
    }

    public Optional<RadarMcpDTO> getLatestForUser(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(
            userId, PageRequest.of(0, 10));
        return page.getContent().stream()
            .filter(r -> r.getStatus() == RadarStatus.READY)
            .findFirst()
            .map(this::toMcp);
    }

    private RadarMcpDTO toMcp(Radar r) {
        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(r.getId());
        var themeDtos = themes.stream().map(this::toThemeMcp).toList();
        return new RadarMcpDTO(r.getId(), r.getGeneratedAt(),
            r.getPeriodStart(), r.getPeriodEnd(), themeDtos);
    }

    private ThemeMcpDTO toThemeMcp(RadarTheme t) {
        var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(t.getId());
        var citations = rtis.stream()
            .limit(3)
            .map(rti -> sourceItemRepo.findById(rti.getSourceItemId()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .map(si -> new CitationMcpDTO(si.getTitle(), si.getDescription(), si.getUrl()))
            .toList();
        return new ThemeMcpDTO(t.getTitle(), t.getSummary(), citations);
    }

    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, id ->
            sourceRepo.findById(id).map(Source::getCode).orElse("unknown"));
    }

    @SuppressWarnings("unchecked")
    private List<Long> preFilterCandidates(List<String> slugs) {
        if (slugs.isEmpty()) return List.of();
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        return em.createQuery(
            "SELECT si.id FROM SourceItem si, SourceItemTag sit, InterestTag it " +
            "WHERE sit.sourceItemId = si.id AND sit.interestTagId = it.id " +
            "AND it.slug IN :slugs AND si.postedAt > :cutoff " +
            "GROUP BY si.id " +
            "ORDER BY MAX(si.postedAt) DESC")
            .setParameter("slugs", slugs)
            .setParameter("cutoff", cutoff)
            .setMaxResults(200)
            .getResultList();
    }
}
