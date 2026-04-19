package com.devradar.repository;

import com.devradar.domain.RadarTheme;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RadarThemeRepository extends JpaRepository<RadarTheme, Long> {
    List<RadarTheme> findByRadarIdOrderByDisplayOrderAsc(Long radarId);
}
