package com.devradar.radar.application;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.*;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RadarSharingService {

    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final SourceRepository sourceRepo;

    private final Map<Long, String> sourceNameCache = new ConcurrentHashMap<>();

    public RadarSharingService(RadarRepository radarRepo,
                               RadarThemeRepository themeRepo,
                               RadarThemeItemRepository themeItemRepo,
                               SourceItemRepository sourceItemRepo,
                               SourceRepository sourceRepo) {
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceRepo = sourceRepo;
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

    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, id ->
            sourceRepo.findById(id).map(Source::getCode).orElse("unknown"));
    }
}
