package com.devradar.web.rest;

import com.devradar.domain.*;
import com.devradar.repository.*;
import com.devradar.web.rest.dto.PublicWeeklyRadarDTO;
import com.devradar.web.rest.dto.RadarItemDTO;
import com.devradar.web.rest.dto.RadarThemeDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/radar")
public class PublicRadarResource {

    private final InterestTagRepository interestTagRepo;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final SourceItemRepository sourceItemRepo;
    private final SourceItemTagRepository sourceItemTagRepo;
    private final SourceRepository sourceRepo;

    private final Map<Long, String> sourceNameCache = new ConcurrentHashMap<>();

    public PublicRadarResource(InterestTagRepository interestTagRepo,
                               RadarRepository radarRepo,
                               RadarThemeRepository themeRepo,
                               RadarThemeItemRepository themeItemRepo,
                               SourceItemRepository sourceItemRepo,
                               SourceItemTagRepository sourceItemTagRepo,
                               SourceRepository sourceRepo) {
        this.interestTagRepo = interestTagRepo;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.sourceItemRepo = sourceItemRepo;
        this.sourceItemTagRepo = sourceItemTagRepo;
        this.sourceRepo = sourceRepo;
    }

    @GetMapping("/{tagSlug}/week/{weekNumber}")
    public ResponseEntity<PublicWeeklyRadarDTO> getWeeklyRadar(
            @PathVariable String tagSlug,
            @PathVariable int weekNumber) {

        // Validate tag exists
        InterestTag tag = interestTagRepo.findBySlug(tagSlug).orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }

        // Calculate week boundaries (ISO week of current year)
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        WeekFields weekFields = WeekFields.ISO;
        LocalDate weekStart = LocalDate.of(year, 1, 1)
                .with(weekFields.weekOfYear(), weekNumber)
                .with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(7);

        Instant periodStart = weekStart.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant periodEnd = weekEnd.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Radar> weekRadars = radarRepo.findPublicReadyOverlapping(RadarStatus.READY, periodStart, periodEnd);

        List<Long> radarIds = weekRadars.stream().map(Radar::getId).toList();
        List<RadarTheme> allThemes = radarIds.isEmpty() ? List.of()
                : themeRepo.findByRadarIdInOrderByDisplayOrderAsc(radarIds);

        List<Long> themeIds = allThemes.stream().map(RadarTheme::getId).toList();
        List<RadarThemeItem> allThemeItems = themeIds.isEmpty() ? List.of()
                : themeItemRepo.findByThemeIdInOrderByDisplayOrderAsc(themeIds);

        List<Long> sourceItemIds = allThemeItems.stream().map(RadarThemeItem::getSourceItemId).distinct().toList();
        Map<Long, SourceItem> sourceItemMap = sourceItemIds.isEmpty() ? Map.of()
                : sourceItemRepo.findAllById(sourceItemIds).stream()
                    .collect(Collectors.toMap(SourceItem::getId, Function.identity()));

        Map<Long, List<SourceItemTag>> tagsByItemId = sourceItemIds.isEmpty() ? Map.of()
                : sourceItemTagRepo.findBySourceItemIdIn(sourceItemIds).stream()
                    .collect(Collectors.groupingBy(SourceItemTag::getSourceItemId));

        Map<Long, List<RadarThemeItem>> themeItemsByThemeId = allThemeItems.stream()
                .collect(Collectors.groupingBy(RadarThemeItem::getThemeId));

        List<RadarThemeDTO> matchingThemes = new ArrayList<>();
        Set<Long> seenThemeTitles = new HashSet<>();

        for (RadarTheme theme : allThemes) {
            List<RadarThemeItem> rtis = themeItemsByThemeId.getOrDefault(theme.getId(), List.of());
            boolean hasMatchingTag = false;
            List<RadarItemDTO> itemDtos = new ArrayList<>();

            for (var rti : rtis) {
                SourceItem si = sourceItemMap.get(rti.getSourceItemId());
                if (si == null) continue;

                List<SourceItemTag> tags = tagsByItemId.getOrDefault(si.getId(), List.of());
                if (tags.stream().anyMatch(t -> t.getInterestTagId().equals(tag.getId()))) {
                    hasMatchingTag = true;
                }

                itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getDescription(),
                        si.getUrl(), si.getAuthor(), resolveSourceName(si.getSourceId())));
            }

            if (hasMatchingTag) {
                long titleHash = theme.getTitle().hashCode();
                if (seenThemeTitles.add(titleHash)) {
                    matchingThemes.add(new RadarThemeDTO(theme.getId(), theme.getTitle(),
                            theme.getSummary(), theme.getDisplayOrder(), itemDtos));
                }
            }
        }

        String title = tag.getDisplayName() + " Ecosystem Radar: Week " + weekNumber + ", " + year;

        return ResponseEntity.ok(new PublicWeeklyRadarDTO(
                title,
                tagSlug,
                tag.getDisplayName(),
                weekNumber,
                year,
                periodStart,
                periodEnd,
                matchingThemes
        ));
    }

    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, id ->
                sourceRepo.findById(id).map(Source::getCode).orElse("unknown"));
    }
}
