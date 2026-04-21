package com.devradar.repository;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceItemRepositoryRecentIT extends AbstractIntegrationTest {

    @Autowired SourceItemRepository items;
    @Autowired SourceRepository sources;
    @Autowired InterestTagRepository tags;
    @Autowired SourceItemTagRepository sitRepo;
    @Autowired UserInterestRepository userInterests;
    @Autowired UserRepository users;

    @Test
    void filtersByUserInterestsAndOptionalTagSlugAndRecency() {
        User u = new User();
        u.setEmail("recent@test.com");
        u.setDisplayName("Recent");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Source source = new Source();
        source.setCode("hn-recent");
        source.setDisplayName("HN");
        source.setActive(true);
        source.setFetchIntervalMinutes(60);
        source = sources.save(source);

        InterestTag java = new InterestTag();
        java.setSlug("java-recent");
        java.setDisplayName("Java");
        java.setCategory(InterestCategory.language);
        java = tags.save(java);

        InterestTag python = new InterestTag();
        python.setSlug("python-recent");
        python.setDisplayName("Python");
        python.setCategory(InterestCategory.language);
        python = tags.save(python);

        userInterests.save(new UserInterest(u.getId(), java.getId()));

        SourceItem matching = persistItem(source.getId(), "ext-1",
            "Spring Boot 3.5 released", Instant.now().minus(2, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(matching.getId(), java.getId()));

        SourceItem nonMatching = persistItem(source.getId(), "ext-2",
            "FastAPI release", Instant.now().minus(2, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(nonMatching.getId(), python.getId()));

        SourceItem stale = persistItem(source.getId(), "ext-3",
            "Spring Boot 2.x EOL", Instant.now().minus(60, ChronoUnit.DAYS));
        sitRepo.save(new SourceItemTag(stale.getId(), java.getId()));

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<SourceItem> out = items.findRecentByUserInterests(u.getId(), since, null, 20);

        assertThat(out).extracting(SourceItem::getExternalId).containsExactly("ext-1");

        List<SourceItem> filtered = items.findRecentByUserInterests(u.getId(), since, "java-recent", 20);
        assertThat(filtered).extracting(SourceItem::getExternalId).containsExactly("ext-1");

        List<SourceItem> noMatch = items.findRecentByUserInterests(u.getId(), since, "python-recent", 20);
        assertThat(noMatch).isEmpty();
    }

    private SourceItem persistItem(Long sourceId, String extId, String title, Instant postedAt) {
        SourceItem si = new SourceItem();
        si.setSourceId(sourceId);
        si.setExternalId(extId);
        si.setUrl("https://example.com/" + extId);
        si.setTitle(title);
        si.setAuthor("tester");
        si.setPostedAt(postedAt);
        return items.save(si);
    }
}
