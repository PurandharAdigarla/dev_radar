package com.devradar.repository;

import com.devradar.domain.Radar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RadarRepository extends JpaRepository<Radar, Long> {
    Page<Radar> findByUserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
}
