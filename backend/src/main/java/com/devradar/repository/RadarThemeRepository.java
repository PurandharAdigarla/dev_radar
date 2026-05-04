package com.devradar.repository;

import com.devradar.domain.RadarTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RadarThemeRepository extends JpaRepository<RadarTheme, Long> {
    List<RadarTheme> findByRadarIdOrderByDisplayOrderAsc(Long radarId);
    List<RadarTheme> findByRadarId(Long radarId);
    List<RadarTheme> findByRadarIdInOrderByDisplayOrderAsc(List<Long> radarIds);
    long countByRadarId(Long radarId);

    @Query("SELECT t.radarId, COUNT(t) FROM RadarTheme t WHERE t.radarId IN :radarIds GROUP BY t.radarId")
    List<Object[]> countThemesByRadarIds(@Param("radarIds") List<Long> radarIds);
}
