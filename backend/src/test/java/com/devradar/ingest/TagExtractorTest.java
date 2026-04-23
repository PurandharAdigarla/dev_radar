package com.devradar.ingest;

import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import com.devradar.repository.InterestTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TagExtractorTest {

    InterestTagRepository repo;
    TagExtractor extractor;

    @BeforeEach
    void setup() {
        repo = mock(InterestTagRepository.class);
        List<InterestTag> tags = List.of(
            tag("spring_boot", "Spring Boot", InterestCategory.framework),
            tag("react", "React", InterestCategory.framework),
            tag("mysql", "MySQL", InterestCategory.database),
            tag("rust", "Rust", InterestCategory.language),
            tag("java", "Java", InterestCategory.language)
        );
        when(repo.findAll()).thenReturn(tags);
        extractor = new TagExtractor(repo);
    }

    @Test
    void extracts_displayName_caseInsensitive() {
        Set<Long> ids = extractor.extract("Spring Boot 3.5 just shipped", null, List.of());
        assertThat(ids).hasSize(1);
    }

    @Test
    void extracts_slugWordBoundary() {
        Set<Long> ids = extractor.extract("Why I'm switching from React to Svelte", null, List.of());
        assertThat(ids).hasSize(1); // react matches; svelte not in our tag set
    }

    @Test
    void extracts_fromExplicitTopics() {
        Set<Long> ids = extractor.extract("Some unrelated title", null, List.of("rust", "mysql"));
        assertThat(ids).hasSize(2);
    }

    @Test
    void noMatch_returnsEmpty() {
        Set<Long> ids = extractor.extract("Nothing relevant here at all", null, List.of());
        assertThat(ids).isEmpty();
    }

    @Test
    void deduplicates_acrossTextAndTopics() {
        Set<Long> ids = extractor.extract("React is great", null, List.of("react"));
        assertThat(ids).hasSize(1);
    }

    @Test
    void extracts_fromDescription() {
        Set<Long> ids = extractor.extract("jextract", "Extract Java bindings from C headers using Panama FFI", List.of());
        assertThat(ids).isNotEmpty();
    }

    private static InterestTag tag(String slug, String display, InterestCategory cat) {
        InterestTag t = new InterestTag();
        t.setSlug(slug);
        t.setDisplayName(display);
        t.setCategory(cat);
        try { var f = InterestTag.class.getDeclaredField("id"); f.setAccessible(true); f.set(t, (long) slug.hashCode()); }
        catch (Exception e) {}
        return t;
    }
}
