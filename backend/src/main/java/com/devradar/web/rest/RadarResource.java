package com.devradar.web.rest;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarRepoRecommendation;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeSource;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.radar.application.TopicRadarApplicationService;
import com.devradar.radar.application.RadarSharingService;
import com.devradar.repository.RadarRepoRecommendationRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.RadarThemeSourceRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/radars")
public class RadarResource {

    private final TopicRadarApplicationService app;
    private final RadarSharingService sharing;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeSourceRepository themeSourceRepo;
    private final RadarRepoRecommendationRepository repoRecRepo;

    public RadarResource(TopicRadarApplicationService app, RadarSharingService sharing,
                         RadarRepository radarRepo, RadarThemeRepository themeRepo,
                         RadarThemeSourceRepository themeSourceRepo,
                         RadarRepoRecommendationRepository repoRecRepo) {
        this.app = app;
        this.sharing = sharing;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeSourceRepo = themeSourceRepo;
        this.repoRecRepo = repoRecRepo;
    }

    @GetMapping
    public Page<RadarSummaryDTO> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        Pageable pageable = PageRequest.of(page, size);
        return radarRepo.findByUserIdOrderByGeneratedAtDesc(uid, pageable)
                .map(r -> new RadarSummaryDTO(r.getId(), r.getStatus(),
                        r.getPeriodStart(), r.getPeriodEnd(), r.getGeneratedAt(),
                        r.getGenerationMs(), r.getTokenCount(),
                        (int) themeRepo.countByRadarId(r.getId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RadarDetailDTO> get(@PathVariable Long id) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        Radar r = radarRepo.findById(id).orElse(null);
        if (r == null) return ResponseEntity.notFound().build();
        if (!r.getUserId().equals(uid)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return ResponseEntity.ok(toDetail(r));
    }

    @PostMapping
    public ResponseEntity<RadarSummaryDTO> create() {
        RadarSummaryDTO created = app.generateForCurrentUser();
        return ResponseEntity.accepted().body(created);
    }

    @GetMapping("/shared/{shareToken}")
    public ResponseEntity<RadarDetailDTO> getShared(@PathVariable String shareToken) {
        return sharing.getByShareToken(shareToken)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/share")
    public ShareRadarResponseDTO share(@PathVariable Long id, HttpServletRequest request) {
        String baseUrl = request.getScheme() + "://" + request.getServerName();
        int port = request.getServerPort();
        if ((request.getScheme().equals("http") && port != 80) || (request.getScheme().equals("https") && port != 443)) {
            baseUrl += ":" + port;
        }
        return sharing.shareRadar(id, baseUrl);
    }

    private RadarDetailDTO toDetail(Radar r) {
        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(r.getId());
        List<Long> themeIds = themes.stream().map(RadarTheme::getId).toList();
        List<RadarThemeSource> allSources = themeIds.isEmpty()
                ? List.of()
                : themeSourceRepo.findByThemeIdIn(themeIds);

        List<RadarThemeDTO> themeDtos = new ArrayList<>();
        for (var t : themes) {
            List<RadarItemDTO> itemDtos = allSources.stream()
                    .filter(s -> s.getThemeId().equals(t.getId()))
                    .map(s -> new RadarItemDTO(s.getId(), s.getTitle(), null, s.getUrl(), null, null))
                    .toList();
            themeDtos.add(new RadarThemeDTO(t.getId(), t.getTitle(), t.getSummary(), t.getDisplayOrder(), itemDtos));
        }

        List<RadarRepoRecommendation> repos = repoRecRepo.findByRadarIdOrderByDisplayOrderAsc(r.getId());
        List<RepoRecommendationDTO> repoDtos = repos.stream()
                .map(rr -> new RepoRecommendationDTO(
                        rr.getRepoUrl(), rr.getRepoName(), rr.getDescription(),
                        rr.getWhyNotable(), rr.getCategory(), rr.getTopic()))
                .toList();

        return new RadarDetailDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount(),
                r.getErrorCode(), r.getErrorMessage(), themeDtos, List.of(), repoDtos);
    }
}
