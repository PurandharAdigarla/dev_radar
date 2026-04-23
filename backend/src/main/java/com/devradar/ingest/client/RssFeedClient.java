package com.devradar.ingest.client;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class RssFeedClient {

    private static final Logger LOG = LoggerFactory.getLogger(RssFeedClient.class);
    private static final int MAX_DESCRIPTION_LENGTH = 2048;

    public List<FetchedItem> fetch(String feedUrl) {
        List<FetchedItem> out = new ArrayList<>();
        try {
            SyndFeed feed = new SyndFeedInput().build(new XmlReader(URI.create(feedUrl).toURL()));
            for (SyndEntry entry : feed.getEntries()) {
                String link = entry.getLink();
                String title = entry.getTitle();
                if (link == null || link.isBlank() || title == null || title.isBlank()) continue;

                String externalId = entry.getUri() != null ? entry.getUri() : link;
                String description = extractDescription(entry);
                String author = extractAuthor(entry);
                Instant postedAt = extractDate(entry);

                out.add(new FetchedItem(
                    externalId, link, title, description, author,
                    postedAt, null, List.of()
                ));
            }
        } catch (Exception e) {
            LOG.warn("failed to parse feed {}: {}", feedUrl, e.toString());
        }
        return out;
    }

    private static String extractDescription(SyndEntry entry) {
        String desc = null;
        if (entry.getDescription() != null) {
            desc = entry.getDescription().getValue();
        }
        if (desc == null || desc.isBlank()) {
            desc = entry.getContents().isEmpty() ? null
                : entry.getContents().get(0).getValue();
        }
        if (desc == null) return null;
        desc = desc.replaceAll("<[^>]+>", "").trim();
        return desc.length() > MAX_DESCRIPTION_LENGTH
            ? desc.substring(0, MAX_DESCRIPTION_LENGTH)
            : desc;
    }

    private static String extractAuthor(SyndEntry entry) {
        if (entry.getAuthor() != null && !entry.getAuthor().isBlank()) {
            return entry.getAuthor();
        }
        if (!entry.getAuthors().isEmpty()) {
            return entry.getAuthors().get(0).getName();
        }
        return null;
    }

    private static Instant extractDate(SyndEntry entry) {
        Date pub = entry.getPublishedDate();
        if (pub != null) return pub.toInstant();
        Date upd = entry.getUpdatedDate();
        if (upd != null) return upd.toInstant();
        return Instant.now();
    }
}
