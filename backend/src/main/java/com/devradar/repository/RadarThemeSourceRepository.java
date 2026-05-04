package com.devradar.repository;

import com.devradar.domain.RadarThemeSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RadarThemeSourceRepository extends JpaRepository<RadarThemeSource, Long> {
    List<RadarThemeSource> findByThemeId(Long themeId);
    List<RadarThemeSource> findByThemeIdIn(List<Long> themeIds);
}
