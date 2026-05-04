package com.devradar.repository;

import com.devradar.domain.RadarRepoRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RadarRepoRecommendationRepository extends JpaRepository<RadarRepoRecommendation, Long> {
    List<RadarRepoRecommendation> findByRadarIdOrderByDisplayOrderAsc(Long radarId);
    List<RadarRepoRecommendation> findByRadarIdIn(List<Long> radarIds);
}
