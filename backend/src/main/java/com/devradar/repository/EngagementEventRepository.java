package com.devradar.repository;

import com.devradar.domain.EngagementEvent;
import com.devradar.domain.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EngagementEventRepository extends JpaRepository<EngagementEvent, Long> {
    List<EngagementEvent> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndEventType(Long userId, EventType eventType);
    List<EngagementEvent> findByUserIdAndRadarId(Long userId, Long radarId);
}
