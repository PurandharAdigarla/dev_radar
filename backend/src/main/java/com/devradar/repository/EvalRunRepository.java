package com.devradar.repository;

import com.devradar.domain.EvalRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalRunRepository extends JpaRepository<EvalRun, Long> {
    List<EvalRun> findAllByOrderByCreatedAtDesc();
}
