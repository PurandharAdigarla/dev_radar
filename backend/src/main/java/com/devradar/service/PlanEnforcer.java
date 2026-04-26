package com.devradar.service;

import com.devradar.domain.Team;
import com.devradar.domain.TeamPlan;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRadarRepository;
import com.devradar.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanEnforcer {

    private static final int TEAM_PLAN_MAX_MEMBERS = 10;
    private static final int TEAM_PLAN_MAX_RADARS_PER_DAY = 5;
    private static final int ENTERPRISE_PLAN_MAX_RADARS_PER_DAY = 50;

    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;
    private final TeamRadarRepository teamRadarRepo;

    public PlanEnforcer(TeamRepository teamRepo, TeamMemberRepository memberRepo, TeamRadarRepository teamRadarRepo) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.teamRadarRepo = teamRadarRepo;
    }

    public void enforceTeamMemberLimit(Long teamId) {
        Team team = teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (team.getPlan() == TeamPlan.ENTERPRISE) return;
        long count = memberRepo.countByTeamId(teamId);
        if (count >= TEAM_PLAN_MAX_MEMBERS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Team plan limited to " + TEAM_PLAN_MAX_MEMBERS + " members. Upgrade to Enterprise for unlimited.");
        }
    }

    public void enforceTeamRadarLimit(Long teamId) {
        Team team = teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        long count = teamRadarRepo.countByTeamId(teamId);
        int limit = team.getPlan() == TeamPlan.ENTERPRISE
            ? ENTERPRISE_PLAN_MAX_RADARS_PER_DAY
            : TEAM_PLAN_MAX_RADARS_PER_DAY;
        if (count >= limit) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Radar generation limit reached for your plan (" + limit + ").");
        }
    }
}
