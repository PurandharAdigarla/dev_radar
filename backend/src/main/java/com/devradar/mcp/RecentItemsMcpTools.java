package com.devradar.mcp;

import com.devradar.domain.SourceItem;
import com.devradar.mcp.dto.RecentItemMcpDTO;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import com.devradar.security.SecurityUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
public class RecentItemsMcpTools {

    private static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 7;
    private static final int ITEM_LIMIT = 20;

    private final SourceItemRepository items;
    private final SourceItemTagRepository itemTagRepo;
    private final SourceRepository sources;
    private final InterestTagRepository tags;
    private final MeterRegistry meters;

    public RecentItemsMcpTools(SourceItemRepository items,
                               SourceItemTagRepository itemTagRepo,
                               SourceRepository sources,
                               InterestTagRepository tags,
                               MeterRegistry meters) {
        this.items = items;
        this.itemTagRepo = itemTagRepo;
        this.sources = sources;
        this.tags = tags;
        this.meters = meters;
    }

    @Tool(name = "get_recent_items",
          description = "Returns ingested items from the last N days that match the authenticated user's interests. Capped at 20 items.")
    public List<RecentItemMcpDTO> getRecentItems(
        @ToolParam(description = "Number of days to look back (1-30, default 7)", required = false) Integer days,
        @ToolParam(description = "Optional interest tag slug to filter by", required = false) String tagSlug) {

        try {
            int n = (days == null) ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
            Long uid = SecurityUtils.getCurrentUserId();
            Instant since = Instant.now().minus(n, ChronoUnit.DAYS);

            List<SourceItem> hits = items.findRecentByUserInterests(uid, since,
                (tagSlug == null || tagSlug.isBlank()) ? null : tagSlug, ITEM_LIMIT);

            List<RecentItemMcpDTO> out = hits.stream().map(this::toDto).toList();
            meters.counter("mcp.tool.calls", "tool", "get_recent_items", "status", "success").increment();
            return out;
        } catch (RuntimeException e) {
            meters.counter("mcp.tool.calls", "tool", "get_recent_items", "status", "error").increment();
            throw e;
        }
    }

    private RecentItemMcpDTO toDto(SourceItem si) {
        String sourceCode = sources.findById(si.getSourceId())
            .map(s -> s.getCode()).orElse("unknown");
        List<String> tagSlugs = itemTagRepo.findBySourceItemId(si.getId()).stream()
            .map(sit -> tags.findById(sit.getInterestTagId())
                .map(it -> it.getSlug()).orElse(null))
            .filter(Objects::nonNull)
            .toList();
        return new RecentItemMcpDTO(si.getTitle(), si.getUrl(), sourceCode, si.getPostedAt(), tagSlugs);
    }
}
