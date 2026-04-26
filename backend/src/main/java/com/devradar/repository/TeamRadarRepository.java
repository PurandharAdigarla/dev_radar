package com.devradar.repository;

import com.devradar.domain.TeamRadar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRadarRepository extends JpaRepository<TeamRadar, Long> {
    List<TeamRadar> findByTeamIdOrderByCreatedAtDesc(Long teamId);
    long countByTeamId(Long teamId);
}
