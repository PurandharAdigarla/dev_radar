package com.devradar.repository;

import com.devradar.domain.UserTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTopicRepository extends JpaRepository<UserTopic, Long> {
    List<UserTopic> findByUserIdOrderByDisplayOrderAsc(Long userId);
    int countByUserId(Long userId);
    void deleteAllByUserId(Long userId);
}
