package com.devradar.eval.application;

import com.devradar.domain.EvalRun;
import com.devradar.eval.EvalService;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import com.devradar.web.rest.dto.EvalRunDTO;
import com.devradar.web.rest.dto.EvalScoreDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EvalApplicationService {

    private final EvalService evalService;
    private final EvalRunRepository evalRunRepository;
    private final EvalScoreRepository evalScoreRepository;

    public EvalApplicationService(EvalService evalService,
                                   EvalRunRepository evalRunRepository,
                                   EvalScoreRepository evalScoreRepository) {
        this.evalService = evalService;
        this.evalRunRepository = evalRunRepository;
        this.evalScoreRepository = evalScoreRepository;
    }

    public EvalRunDTO triggerRun(int radarCount) {
        EvalRun run = evalService.runEval(radarCount);
        return toDto(run);
    }

    public List<EvalRunDTO> listRuns() {
        return evalRunRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    private EvalRunDTO toDto(EvalRun run) {
        var scores = evalScoreRepository.findByEvalRunId(run.getId()).stream()
                .map(s -> new EvalScoreDTO(s.getCategory().name(), s.getScore()))
                .toList();

        return new EvalRunDTO(
                run.getId(),
                run.getStatus().name(),
                run.getRadarCount(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getErrorMessage(),
                scores
        );
    }
}
