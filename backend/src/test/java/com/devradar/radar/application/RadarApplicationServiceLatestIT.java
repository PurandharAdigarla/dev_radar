package com.devradar.radar.application;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.User;
import com.devradar.mcp.dto.RadarMcpDTO;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RadarApplicationServiceLatestIT extends AbstractIntegrationTest {

    @Autowired RadarApplicationService appService;
    @Autowired RadarRepository radarRepo;
    @Autowired UserRepository userRepo;

    @Test
    void returnsEmptyWhenUserHasNoReadyRadar() {
        User u = persistUser("latest-none@test.com");
        Optional<RadarMcpDTO> out = appService.getLatestForUser(u.getId());
        assertThat(out).isEmpty();
    }

    @Test
    void returnsLatestReadyRadar() {
        User u = persistUser("latest-some@test.com");
        Radar older = persistRadar(u.getId(), Instant.now().minusSeconds(3600));
        Radar newer = persistRadar(u.getId(), Instant.now());

        Optional<RadarMcpDTO> out = appService.getLatestForUser(u.getId());

        assertThat(out).isPresent();
        assertThat(out.get().radarId()).isEqualTo(newer.getId());
    }

    private User persistUser(String email) {
        User u = new User();
        u.setEmail(email);
        u.setDisplayName("Tester");
        u.setPasswordHash("h");
        u.setActive(true);
        return userRepo.save(u);
    }

    private Radar persistRadar(Long userId, Instant generatedAt) {
        Radar r = new Radar();
        r.setUserId(userId);
        r.setStatus(RadarStatus.READY);
        r.setPeriodStart(generatedAt.minusSeconds(604800));
        r.setPeriodEnd(generatedAt);
        r.setGeneratedAt(generatedAt);
        r.setGenerationMs(1000L);
        r.setTokenCount(100);
        return radarRepo.save(r);
    }
}
