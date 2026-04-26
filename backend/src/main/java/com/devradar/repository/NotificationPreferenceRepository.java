package com.devradar.repository;

import com.devradar.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByUserId(Long userId);
    List<NotificationPreference> findByEmailEnabledTrueAndDigestDayOfWeekAndDigestHourUtc(int dayOfWeek, int hourUtc);
}
