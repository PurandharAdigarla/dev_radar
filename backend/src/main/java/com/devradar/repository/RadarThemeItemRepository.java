package com.devradar.repository;

import com.devradar.domain.RadarThemeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RadarThemeItemRepository extends JpaRepository<RadarThemeItem, Long> {
    List<RadarThemeItem> findByThemeIdOrderByDisplayOrderAsc(Long themeId);
}
