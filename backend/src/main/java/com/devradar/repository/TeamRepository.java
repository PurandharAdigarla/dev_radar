package com.devradar.repository;

import com.devradar.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findBySlug(String slug);
    List<Team> findByOwnerId(Long ownerId);
}
