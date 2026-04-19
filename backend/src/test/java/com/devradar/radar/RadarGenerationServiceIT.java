package com.devradar.radar;

import com.devradar.AbstractIntegrationTest;
import com.devradar.ai.AiClient;
import com.devradar.ai.AiResponse;
import com.devradar.domain.*;
import com.devradar.repository.*;
import com.devradar.security.JwtUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RadarGenerationServiceIT extends AbstractIntegrationTest {

    @MockBean AiClient ai;

    @Autowired UserRepository userRepo;
    @Autowired UserInterestRepository userInterestRepo;
    @Autowired InterestTagRepository tagRepo;
    @Autowired SourceRepository sourceRepo;
    @Autowired SourceItemRepository sourceItemRepo;
    @Autowired SourceItemTagRepository sourceItemTagRepo;
    @Autowired RadarRepository radarRepo;
    @Autowired RadarThemeRepository themeRepo;

    @Autowired com.devradar.radar.application.RadarApplicationService app;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void createRadar_runsAgentLoop_persistsThemes_andMarksReady() throws Exception {
        // Given a user with one interest, one source_item tagged with that interest
        User u = new User();
        u.setEmail("radar@example.com");
        u.setDisplayName("R");
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuvWXYZabcdefghijklmnopqrstuvWXYZ1");
        u.setActive(true);
        u = userRepo.save(u);

        InterestTag spring = tagRepo.findBySlug("spring_boot").orElseThrow();
        userInterestRepo.save(new UserInterest(u.getId(), spring.getId()));

        Source hn = sourceRepo.findByCode("HN").orElseThrow();
        SourceItem si = new SourceItem();
        si.setSourceId(hn.getId());
        si.setExternalId("rad-1");
        si.setUrl("https://example.com/sb35");
        si.setTitle("Spring Boot 3.5");
        si.setPostedAt(Instant.now());
        si = sourceItemRepo.save(si);
        sourceItemTagRepo.save(new SourceItemTag(si.getId(), spring.getId()));
        Long siId = si.getId();

        // Given the AI returns a single end_turn with the radar JSON
        when(ai.generate(anyString(), anyString(), anyList(), anyList(), anyInt()))
            .thenReturn(new AiResponse(
                "{\"themes\":[{\"title\":\"Spring 3.5\",\"summary\":\"VTs default\",\"item_ids\":[" + siId + "]}]}",
                List.of(), "end_turn", 200, 100));

        // Auth context for the user
        var auth = new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of());
        auth.setDetails(new JwtUserDetails(u.getId(), u.getEmail()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        var summary = app.createForCurrentUser();
        Long radarId = summary.id();

        await().atMost(15, TimeUnit.SECONDS).until(() -> {
            var r = radarRepo.findById(radarId).orElseThrow();
            return r.getStatus() == RadarStatus.READY;
        });

        var themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radarId);
        assertThat(themes).hasSize(1);
        assertThat(themes.get(0).getTitle()).isEqualTo("Spring 3.5");
    }
}
