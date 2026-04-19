package com.devradar.ingest;

import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.ingest.client.FetchedItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class IngestionService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionService.class);
    private static final Duration DEDUP_TTL = Duration.ofMinutes(5);

    private final SourceItemRepository itemRepo;
    private final SourceItemTagRepository tagRepo;
    private final TagExtractor tagExtractor;
    private final DedupService dedup;

    public IngestionService(
        SourceItemRepository itemRepo,
        SourceItemTagRepository tagRepo,
        TagExtractor tagExtractor,
        DedupService dedup
    ) {
        this.itemRepo = itemRepo;
        this.tagRepo = tagRepo;
        this.tagExtractor = tagExtractor;
        this.dedup = dedup;
    }

    /**
     * Ingest a batch of items for one source. Per-item failures are logged and skipped.
     * Returns count of newly persisted items.
     */
    public int ingestBatch(Source source, List<FetchedItem> items) {
        int inserted = 0;
        for (FetchedItem item : items) {
            try {
                if (ingestOne(source, item)) inserted++;
            } catch (Exception e) {
                LOG.warn("ingest item failed source={} extId={}: {}", source.getCode(), item.externalId(), e.toString());
            }
        }
        LOG.info("ingest source={} fetched={} inserted={}", source.getCode(), items.size(), inserted);
        return inserted;
    }

    /**
     * Per-item insert. NOT @Transactional — Spring's proxy is bypassed when called from same-class
     * ingestBatch(). Each repo.save() runs in its own JPA tx (Spring Data JPA default). If the tag
     * inserts fail mid-loop, we'll have an orphan source_item with partial tags; cleanup is acceptable
     * for ingestion (no user impact) and surfaces via observability later.
     */
    private boolean ingestOne(Source source, FetchedItem item) {
        String dedupKey = "ingest:" + source.getCode() + ":" + item.externalId();

        // Two-stage dedup: cheap Redis SETNX, then DB unique constraint as backstop.
        if (!dedup.tryAcquire(dedupKey, DEDUP_TTL)) return false;
        if (itemRepo.existsBySourceIdAndExternalId(source.getId(), item.externalId())) return false;

        SourceItem si = new SourceItem();
        si.setSourceId(source.getId());
        si.setExternalId(item.externalId());
        si.setUrl(item.url());
        si.setTitle(item.title());
        si.setAuthor(item.author());
        si.setPostedAt(item.postedAt());
        si.setRawPayload(item.rawPayload());
        SourceItem saved = itemRepo.save(si);

        Set<Long> tagIds = tagExtractor.extract(item.title(), item.topics());
        for (Long tagId : tagIds) {
            tagRepo.save(new SourceItemTag(saved.getId(), tagId));
        }
        return true;
    }
}
