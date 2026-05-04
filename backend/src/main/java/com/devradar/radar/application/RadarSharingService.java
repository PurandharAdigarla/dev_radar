package com.devradar.radar.application;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeSource;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.*;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RadarSharingService {

    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeSourceRepository themeSourceRepo;

    public RadarSharingService(RadarRepository radarRepo,
                               RadarThemeRepository themeRepo,
                               RadarThemeSourceRepository themeSourceRepo) {
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeSourceRepo = themeSourceRepo;
    }

    public ShareRadarResponseDTO shareRadar(Long radarId, String baseUrl) {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        Radar r = radarRepo.findById(radarId).orElseThrow();
        if (!r.getUserId().equals(uid)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        if (r.getShareToken() == null) {
            r.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        }
        r.setPublic(true);
        radarRepo.save(r);

        String shareUrl = baseUrl + "/radar/shared/" + r.getShareToken();
        return new ShareRadarResponseDTO(r.getShareToken(), shareUrl);
    }

    public Optional<RadarDetailDTO> getByShareToken(String shareToken) {
        return radarRepo.findByShareToken(shareToken)
                .filter(Radar::isPublic)
                .map(this::toDetail);
    }

    public Optional<RadarDetailDTO> getLatestPublicRadar() {
        return radarRepo.findFirstByIsPublicTrueAndStatusOrderByGeneratedAtDesc(RadarStatus.READY)
                .map(this::toDetail);
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
                    .map(s -> new RadarItemDTO(null, s.getTitle(), null, s.getUrl(), null, null))
                    .toList();
            themeDtos.add(new RadarThemeDTO(t.getId(), t.getTitle(), t.getSummary(), t.getDisplayOrder(), itemDtos));
        }

        List<WebSourceDTO> webSources = allSources.stream()
                .filter(s -> s.getUrl() != null && !s.getUrl().isBlank())
                .map(s -> new WebSourceDTO(s.getUrl(), s.getTitle()))
                .toList();

        return new RadarDetailDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount(),
                r.getErrorCode(), r.getErrorMessage(), themeDtos, webSources);
    }
}
