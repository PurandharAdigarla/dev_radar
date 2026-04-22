package com.devradar.ingest.client;

import java.time.Instant;
import java.util.List;

/**
 * Source-agnostic DTO produced by source clients and consumed by IngestionService.
 * `topics` are slugs the source itself has tagged the item with (e.g., GitHub repo topics).
 */
public record FetchedItem(
    String externalId,
    String url,
    String title,
    String description,
    String author,
    Instant postedAt,
    String rawPayload,
    List<String> topics
) {}
