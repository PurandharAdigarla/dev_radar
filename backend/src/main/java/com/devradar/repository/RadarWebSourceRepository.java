package com.devradar.repository;

import com.devradar.domain.RadarWebSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RadarWebSourceRepository extends JpaRepository<RadarWebSource, Long> {
    List<RadarWebSource> findByRadarId(Long radarId);
}
