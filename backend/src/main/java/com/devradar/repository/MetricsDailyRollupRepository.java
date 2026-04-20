package com.devradar.repository;

import com.devradar.domain.MetricsDailyRollup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MetricsDailyRollupRepository extends JpaRepository<MetricsDailyRollup, LocalDate> {
    List<MetricsDailyRollup> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
