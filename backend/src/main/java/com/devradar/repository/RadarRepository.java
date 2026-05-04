package com.devradar.repository;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    List<Radar> findByStatusAndPeriodEndBefore(RadarStatus status, Instant cutoff);

    @Query("""
        SELECT r FROM Radar r
         WHERE r.status = :status AND r.isPublic = true
           AND r.periodStart IS NOT NULL AND r.periodEnd IS NOT NULL
           AND r.periodEnd >= :periodStart AND r.periodStart <= :periodEnd
         ORDER BY r.generatedAt DESC
        """)
    List<Radar> findPublicReadyOverlapping(@Param("status") RadarStatus status,
                                           @Param("periodStart") Instant periodStart,
                                           @Param("periodEnd") Instant periodEnd);
}
