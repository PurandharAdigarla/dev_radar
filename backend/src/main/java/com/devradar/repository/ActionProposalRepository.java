package com.devradar.repository;

import com.devradar.domain.ActionProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActionProposalRepository extends JpaRepository<ActionProposal, Long> {
    List<ActionProposal> findByRadarIdOrderByCreatedAtAsc(Long radarId);
    List<ActionProposal> findByUserIdOrderByCreatedAtDesc(Long userId);
}
