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
import java.time.temporal.ChronoField;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

        // Find all public/ready radars that overlap this week
        List<Radar> radars = radarRepo.findByStatusOrderByGeneratedAtDesc(RadarStatus.READY,
                org.springframework.data.domain.PageRequest.of(0, 500)).getContent();

        // Filter to radars whose period overlaps this week
        List<Radar> weekRadars = radars.stream()
                .filter(Radar::isPublic)
                .filter(r -> r.getPeriodStart() != null && r.getPeriodEnd() != null)
                .filter(r -> !r.getPeriodEnd().isBefore(periodStart) && !r.getPeriodStart().isAfter(periodEnd))
                .toList();

        // Collect themes from these radars that contain items tagged with our slug
        List<RadarThemeDTO> matchingThemes = new ArrayList<>();
        Set<Long> seenThemeTitles = new HashSet<>();

        for (Radar radar : weekRadars) {
            List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radar.getId());
            for (RadarTheme theme : themes) {
                var rtis = themeItemRepo.findByThemeIdOrderByDisplayOrderAsc(theme.getId());
                boolean hasMatchingTag = false;
                List<RadarItemDTO> itemDtos = new ArrayList<>();

                for (var rti : rtis) {
                    SourceItem si = sourceItemRepo.findById(rti.getSourceItemId()).orElse(null);
                    if (si == null) continue;

                    // Check if this source item is tagged with our interest tag
                    List<SourceItemTag> tags = sourceItemTagRepo.findBySourceItemId(si.getId());
                    boolean tagged = tags.stream().anyMatch(t -> t.getInterestTagId().equals(tag.getId()));
                    if (tagged) {
                        hasMatchingTag = true;
                    }

                    itemDtos.add(new RadarItemDTO(si.getId(), si.getTitle(), si.getDescription(),
                            si.getUrl(), si.getAuthor(), resolveSourceName(si.getSourceId())));
                }

                if (hasMatchingTag) {
                    // Deduplicate by title hash
                    long titleHash = theme.getTitle().hashCode();
                    if (seenThemeTitles.add(titleHash)) {
                        matchingThemes.add(new RadarThemeDTO(theme.getId(), theme.getTitle(),
                                theme.getSummary(), theme.getDisplayOrder(), itemDtos));
                    }
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
