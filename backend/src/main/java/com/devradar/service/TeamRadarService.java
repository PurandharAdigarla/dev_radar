package com.devradar.service;

import com.devradar.domain.*;
import com.devradar.radar.RadarGenerationService;
import com.devradar.radar.RadarService;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRadarRepository;
import com.devradar.web.rest.dto.RadarSummaryDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class TeamRadarService {

    private final TeamMemberRepository memberRepo;
    private final TeamRadarRepository teamRadarRepo;
    private final UserInterestService interestService;
    private final RadarService radarService;
    private final RadarGenerationService generation;
    private final PlanEnforcer planEnforcer;

    @PersistenceContext private EntityManager em;

    public TeamRadarService(TeamMemberRepository memberRepo,
                            TeamRadarRepository teamRadarRepo,
                            UserInterestService interestService,
                            RadarService radarService,
                            RadarGenerationService generation,
                            PlanEnforcer planEnforcer) {
        this.memberRepo = memberRepo;
        this.teamRadarRepo = teamRadarRepo;
        this.interestService = interestService;
        this.radarService = radarService;
        this.generation = generation;
        this.planEnforcer = planEnforcer;
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
        List<Long> candidateIds = preFilterCandidates(slugList);

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

    @SuppressWarnings("unchecked")
    private List<Long> preFilterCandidates(List<String> slugs) {
        if (slugs.isEmpty()) return List.of();
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        return em.createQuery(
            "SELECT si.id FROM SourceItem si, SourceItemTag sit, InterestTag it " +
            "WHERE sit.sourceItemId = si.id AND sit.interestTagId = it.id " +
            "AND it.slug IN :slugs AND si.postedAt > :cutoff " +
            "GROUP BY si.id " +
            "ORDER BY MAX(si.postedAt) DESC")
            .setParameter("slugs", slugs)
            .setParameter("cutoff", cutoff)
            .setMaxResults(200)
            .getResultList();
    }
}
