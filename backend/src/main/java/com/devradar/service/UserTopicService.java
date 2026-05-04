package com.devradar.service;

import com.devradar.agent.TopicValidationAgent;
import com.devradar.agent.TopicValidationAgent.ValidatedTopic;
import com.devradar.domain.UserTopic;
import com.devradar.repository.UserTopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserTopicService {

    private static final Logger LOG = LoggerFactory.getLogger(UserTopicService.class);
    private static final int MAX_TOPICS = 10;
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 80;

    private final UserTopicRepository repo;
    private final TopicValidationAgent validationAgent;

    public UserTopicService(UserTopicRepository repo, TopicValidationAgent validationAgent) {
        this.repo = repo;
        this.validationAgent = validationAgent;
    }

    public List<UserTopic> getTopics(Long userId) {
        return repo.findByUserIdOrderByDisplayOrderAsc(userId);
    }

    @Transactional
    public List<UserTopic> setTopics(Long userId, List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("At least one topic is required");
        }
        if (topics.size() > MAX_TOPICS) {
            throw new IllegalArgumentException("Maximum " + MAX_TOPICS + " topics allowed");
        }

        List<String> cleaned = topics.stream()
                .map(String::trim)
                .filter(t -> t.length() >= MIN_LENGTH && t.length() <= MAX_LENGTH)
                .distinct()
                .toList();

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("No valid topics provided (min " + MIN_LENGTH + " characters each)");
        }

        List<ValidatedTopic> validated = validationAgent.validate(cleaned);
        List<String> invalid = validated.stream()
                .filter(v -> !v.valid())
                .map(ValidatedTopic::topic)
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException("Invalid topics: " + String.join(", ", invalid));
        }

        repo.deleteAllByUserId(userId);

        List<UserTopic> saved = new ArrayList<>();
        int order = 0;
        for (ValidatedTopic v : validated) {
            UserTopic ut = new UserTopic();
            ut.setUserId(userId);
            ut.setTopic(v.normalized());
            ut.setDisplayOrder(order++);
            saved.add(repo.save(ut));
        }

        LOG.info("set {} topics for userId={}", saved.size(), userId);
        return saved;
    }
}
