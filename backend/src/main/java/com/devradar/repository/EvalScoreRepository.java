package com.devradar.repository;

import com.devradar.domain.EvalScore;
import com.devradar.domain.EvalScoreCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvalScoreRepository extends JpaRepository<EvalScore, Long> {
    List<EvalScore> findByEvalRunId(Long evalRunId);
    Optional<EvalScore> findTopByCategory(EvalScoreCategory category);
}
