package com.devradar.repository;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RadarRepository extends JpaRepository<Radar, Long> {
    Page<Radar> findByUserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);

    Page<Radar> findByStatusOrderByGeneratedAtDesc(RadarStatus status, Pageable pageable);

    @Query("SELECT COUNT(r) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    int countReadyByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(r.inputTokenCount), 0) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    long sumInputTokensByDate(@Param("date") LocalDate date);

    @Query("SELECT COALESCE(SUM(r.outputTokenCount), 0) FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date")
    long sumOutputTokensByDate(@Param("date") LocalDate date);

    @Query("SELECT r.generationMs FROM Radar r WHERE r.status = 'READY' AND FUNCTION('DATE', r.generatedAt) = :date ORDER BY r.generationMs ASC")
    List<Long> findGenerationMsByDate(@Param("date") LocalDate date);

    Optional<Radar> findByShareToken(String shareToken);

    Optional<Radar> findFirstByIsPublicTrueAndStatusOrderByGeneratedAtDesc(RadarStatus status);

    long countByUserIdAndStatus(Long userId, RadarStatus status);
}
