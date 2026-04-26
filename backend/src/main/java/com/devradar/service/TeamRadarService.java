package com.devradar.service;

import com.devradar.domain.*;
import com.devradar.radar.RadarGenerationService;
import com.devradar.radar.RadarService;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRadarRepository;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class TeamRadarService {

    private final TeamMemberRepository memberRepo;
    private final TeamRadarRepository teamRadarRepo;
    private final UserInterestService interestService;
    private final RadarService radarService;
    private final RadarGenerationService generation;
    private final PlanEnforcer planEnforcer;
    private final SourceItemRepository sourceItemRepo;

    public TeamRadarService(TeamMemberRepository memberRepo,
                            TeamRadarRepository teamRadarRepo,
                            UserInterestService interestService,
                            RadarService radarService,
                            RadarGenerationService generation,
                            PlanEnforcer planEnforcer,
                            SourceItemRepository sourceItemRepo) {
        this.memberRepo = memberRepo;
        this.teamRadarRepo = teamRadarRepo;
        this.interestService = interestService;
        this.radarService = radarService;
        this.generation = generation;
        this.planEnforcer = planEnforcer;
        this.sourceItemRepo = sourceItemRepo;
    }

    @Transactional
    public RadarSummaryDTO createTeamRadar(Long teamId, Long requesterId) {
        planEnforcer.enforceTeamRadarLimit(teamId);

        List<TeamMember> members = memberRepo.findByTeamId(teamId);
        Set<String> mergedSlugs = new LinkedHashSet<>();
        for (TeamMember m : members) {
            List<InterestTag> tags = interestService.findInterestsForUser(m.getUserId());
            for (InterestTag t : tags) {
                mergedSlugs.add(t.getSlug());
            }
        }

        List<String> slugList = List.copyOf(mergedSlugs);
        List<Long> candidateIds = sourceItemRepo.preFilterCandidates(slugList);

        Radar radar = radarService.createPending(requesterId);
        generation.runGeneration(radar.getId(), requesterId, slugList, candidateIds);

        TeamRadar tr = new TeamRadar();
        tr.setTeamId(teamId);
        tr.setRadarId(radar.getId());
        teamRadarRepo.save(tr);

        return new RadarSummaryDTO(
            radar.getId(), radar.getStatus(),
            radar.getPeriodStart(), radar.getPeriodEnd(),
            radar.getGeneratedAt(), radar.getGenerationMs(),
            radar.getTokenCount(), 0
        );
    }

    public List<TeamRadar> getTeamRadars(Long teamId) {
        return teamRadarRepo.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

}
