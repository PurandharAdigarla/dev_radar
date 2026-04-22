package com.devradar.ingest;

import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TagExtractor {

    private final InterestTagRepository repo;

    public TagExtractor(InterestTagRepository repo) { this.repo = repo; }

    /**
     * Extract interest_tag IDs that match in the given title or description, or are explicitly listed in topics.
     * Match is case-insensitive substring with word-boundary intent.
     */
    public Set<Long> extract(String title, String description, List<String> explicitTopics) {
        String titlePart = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String descPart = description == null ? "" : description.toLowerCase(Locale.ROOT);
        String hay = titlePart + " " + descPart;
        Set<String> topicSlugs = explicitTopics == null ? Set.of()
            : explicitTopics.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).collect(java.util.stream.Collectors.toSet());

        Set<Long> matched = new HashSet<>();
        for (InterestTag tag : repo.findAll()) {
            String slug = tag.getSlug().toLowerCase(Locale.ROOT);
            String displayLower = tag.getDisplayName().toLowerCase(Locale.ROOT);

            boolean inText = containsAsWord(hay, displayLower) || containsAsWord(hay, slug);
            boolean inTopics = topicSlugs.contains(slug);

            if (inText || inTopics) matched.add(tag.getId());
        }
        return matched;
    }

    private static boolean containsAsWord(String hay, String needle) {
        if (needle.isBlank()) return false;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) != -1) {
            boolean leftOk = (idx == 0) || !Character.isLetterOrDigit(hay.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = (end == hay.length()) || !Character.isLetterOrDigit(hay.charAt(end));
            if (leftOk && rightOk) return true;
            idx = end;
        }
        return false;
    }
}
