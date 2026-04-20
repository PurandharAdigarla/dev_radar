package com.devradar.action.application;

import com.devradar.action.AutoPrExecutor;
import com.devradar.domain.ActionProposal;
import com.devradar.domain.ActionProposalStatus;
import com.devradar.domain.exception.UserNotAuthenticatedException;
import com.devradar.repository.ActionProposalRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.web.rest.dto.ActionProposalDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActionApplicationService {

    private final ActionProposalRepository repo;
    private final AutoPrExecutor executor;

    public ActionApplicationService(ActionProposalRepository repo, AutoPrExecutor executor) {
        this.repo = repo; this.executor = executor;
    }

    public List<ActionProposalDTO> listForRadar(Long radarId) {
        Long uid = currentUserId();
        return repo.findByRadarIdOrderByCreatedAtAsc(radarId).stream()
            .filter(p -> p.getUserId().equals(uid))
            .map(this::toDto)
            .toList();
    }

    public ActionProposalDTO approve(Long proposalId, String fixVersion) {
        Long uid = currentUserId();
        ActionProposal p = repo.findById(proposalId).orElseThrow();
        if (!p.getUserId().equals(uid)) throw new RuntimeException("forbidden");
        executor.execute(proposalId, fixVersion);
        return toDto(repo.findById(proposalId).orElseThrow());
    }

    @Transactional
    public ActionProposalDTO dismiss(Long proposalId) {
        Long uid = currentUserId();
        ActionProposal p = repo.findById(proposalId).orElseThrow();
        if (!p.getUserId().equals(uid)) throw new RuntimeException("forbidden");
        p.setStatus(ActionProposalStatus.DISMISSED);
        return toDto(repo.save(p));
    }

    private Long currentUserId() {
        Long uid = SecurityUtils.getCurrentUserId();
        if (uid == null) throw new UserNotAuthenticatedException();
        return uid;
    }

    private ActionProposalDTO toDto(ActionProposal p) {
        return new ActionProposalDTO(p.getId(), p.getRadarId(), p.getKind(), p.getPayload(),
            p.getStatus(), p.getPrUrl(), p.getFailureReason(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
