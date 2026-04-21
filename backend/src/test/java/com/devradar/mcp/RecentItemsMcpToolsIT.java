package com.devradar.mcp;

import com.devradar.AbstractIntegrationTest;
import com.devradar.apikey.ApiKeyService;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.InterestCategory;
import com.devradar.domain.InterestTag;
import com.devradar.domain.Source;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.domain.User;
import com.devradar.domain.UserInterest;
import com.devradar.mcp.dto.RecentItemMcpDTO;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import com.devradar.repository.UserInterestRepository;
import com.devradar.repository.UserRepository;
import com.devradar.security.ApiKeyAuthenticationToken;
import com.devradar.security.ApiKeyPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentItemsMcpToolsIT extends AbstractIntegrationTest {

    @Autowired RecentItemsMcpTools tool;
    @Autowired ToolCallbackProvider toolCallbackProvider;
    @Autowired UserRepository users;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository itemTags;
    @Autowired InterestTagRepository tags;
    @Autowired UserInterestRepository userInterests;
    @Autowired ApiKeyService apiKeys;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRecentItemsToolIsRegistered() {
        List<String> toolNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
            .map(cb -> cb.getToolDefinition().name())
            .toList();
        assertThat(toolNames).contains("get_recent_items");
    }

    @Test
    void returnsItemsMatchingUserInterestsInRecencyWindow() {
        User u = new User();
        u.setEmail("recent-mcp@test.com");
        u.setDisplayName("Recent");
        u.setPasswordHash("h");
        u.setActive(true);
        u = users.save(u);

        Source src = new Source();
        src.setCode("hn-mcp-recent");
        src.setDisplayName("HN");
        src.setActive(true);
        src.setFetchIntervalMinutes(60);
        src = sources.save(src);

        InterestTag t = new InterestTag();
        t.setSlug("java-mcp-recent");
        t.setDisplayName("Java");
        t.setCategory(InterestCategory.language);
        t = tags.save(t);

        userInterests.save(new UserInterest(u.getId(), t.getId()));

        SourceItem si = new SourceItem();
        si.setSourceId(src.getId());
        si.setExternalId("recent-mcp-1");
        si.setUrl("https://example.com/mcp-a");
        si.setTitle("Spring Boot release");
        si.setAuthor("tester");
        si.setPostedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        si = items.save(si);
        itemTags.save(new SourceItemTag(si.getId(), t.getId()));

        apiKeys.generate(u.getId(), "recent", ApiKeyScope.READ);
        SecurityContextHolder.getContext().setAuthentication(
            new ApiKeyAuthenticationToken(new ApiKeyPrincipal(u.getId(), 1L, ApiKeyScope.READ)));

        List<RecentItemMcpDTO> out = tool.getRecentItems(7, null);

        assertThat(out).isNotEmpty();
        assertThat(out.getFirst().title()).isEqualTo("Spring Boot release");
        assertThat(out.getFirst().url()).isEqualTo("https://example.com/mcp-a");
        assertThat(out.getFirst().source()).isEqualTo("hn-mcp-recent");
        assertThat(out.getFirst().tags()).contains("java-mcp-recent");
    }
}
