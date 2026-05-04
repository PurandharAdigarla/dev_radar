package com.devradar.web.rest;

import com.devradar.domain.UserTopic;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.security.SecurityUtils;
import com.devradar.service.UserTopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/me/topics")
public class TopicResource {

    private final UserTopicService topicService;

    public TopicResource(UserTopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<TopicDTO>> getTopics() {
        Long userId = requireUser();
        var topics = topicService.getTopics(userId);
        return ResponseEntity.ok(topics.stream().map(TopicResource::toDto).toList());
    }

    @PutMapping
    public ResponseEntity<?> setTopics(@RequestBody SetTopicsRequest request) {
        Long userId = requireUser();
        try {
            var saved = topicService.setTopics(userId, request.topics());
            return ResponseEntity.ok(saved.stream().map(TopicResource::toDto).toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Long requireUser() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }

    private static TopicDTO toDto(UserTopic ut) {
        return new TopicDTO(ut.getId(), ut.getTopic(), ut.getDisplayOrder());
    }

    public record TopicDTO(Long id, String topic, int displayOrder) {}
    public record SetTopicsRequest(List<String> topics) {}
}
