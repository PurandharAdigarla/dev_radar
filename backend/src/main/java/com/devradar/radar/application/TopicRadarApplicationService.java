package com.devradar.radar.application;

import com.devradar.agent.RadarGenerationAgent;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.UserTopic;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.radar.RadarService;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.UserTopicRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class TopicRadarApplicationService {

    private final RadarService radarService;
    private final RadarGenerationAgent generationAgent;
    private final RadarRepository radarRepo;
    private final UserTopicRepository topicRepo;

    public TopicRadarApplicationService(RadarService radarService,
                                        RadarGenerationAgent generationAgent,
                                        RadarRepository radarRepo,
                                        UserTopicRepository topicRepo) {
        this.radarService = radarService;
        this.generationAgent = generationAgent;
        this.radarRepo = radarRepo;
        this.topicRepo = topicRepo;
    }

    @Transactional
    public RadarSummaryDTO generateForCurrentUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();

        checkDailyLimit(uid);

        List<UserTopic> topics = topicRepo.findByUserIdOrderByDisplayOrderAsc(uid);
        if (topics.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No topics configured. Add topics before generating a radar.");
        }

        List<String> topicNames = topics.stream().map(UserTopic::getTopic).toList();

        Radar created = radarService.createPending(uid);
        generationAgent.runGeneration(created.getId(), uid, topicNames);

        return new RadarSummaryDTO(created.getId(), created.getStatus(),
                created.getPeriodStart(), created.getPeriodEnd(),
                created.getGeneratedAt(), created.getGenerationMs(),
                created.getTokenCount(), 0);
    }

    private void checkDailyLimit(Long userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startOfDay = today.atStartOfDay(ZoneOffset.UTC).toInstant();

        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, 5));
        boolean generatedToday = page.getContent().stream()
                .anyMatch(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(startOfDay));

        long todayCount = page.getContent().stream()
                .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(startOfDay))
                .count();
        if (todayCount >= 10) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily radar limit reached. Try again tomorrow.");
        }
    }
}
