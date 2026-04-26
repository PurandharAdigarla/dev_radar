package com.devradar.web.rest;

import com.devradar.domain.*;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.security.SecurityUtils;
import com.devradar.service.TeamAuthorizationService;
import com.devradar.service.TeamRadarService;
import com.devradar.service.TeamService;
import com.devradar.web.rest.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
public class TeamResource {

    private final TeamService teamService;
    private final TeamAuthorizationService authz;
    private final TeamRadarService teamRadarService;
    private final TeamMemberRepository memberRepo;
    private final TeamRepository teamRepo;
    private final RadarRepository radarRepo;

    public TeamResource(TeamService teamService,
                        TeamAuthorizationService authz,
                        TeamRadarService teamRadarService,
                        TeamMemberRepository memberRepo,
                        TeamRepository teamRepo,
                        RadarRepository radarRepo) {
        this.teamService = teamService;
        this.authz = authz;
        this.teamRadarService = teamRadarService;
        this.memberRepo = memberRepo;
        this.teamRepo = teamRepo;
        this.radarRepo = radarRepo;
    }

    @PostMapping
    public ResponseEntity<TeamDTO> create(@RequestBody CreateTeamDTO dto) {
        Long userId = requireUserId();
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team name is required");
        }
        Team team = teamService.createTeam(dto.name().trim(), userId);
        return ResponseEntity.status(201).body(toDto(team));
    }

    @GetMapping
    public List<TeamDTO> listMyTeams() {
        Long userId = requireUserId();
        return teamService.getTeamsForUser(userId).stream().map(this::toDto).toList();
    }

    @GetMapping("/{teamId}")
    public TeamDTO get(@PathVariable Long teamId) {
        Long userId = requireUserId();
        Team team = findTeamOrThrow(teamId);
        authz.requireTeamMember(teamId, userId);
        return toDto(team);
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberDTO> addMember(@PathVariable Long teamId, @RequestBody AddTeamMemberDTO dto) {
        Long userId = requireUserId();
        findTeamOrThrow(teamId);
        TeamRole role;
        try {
            role = dto.role() != null ? TeamRole.valueOf(dto.role()) : TeamRole.MEMBER;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role: " + dto.role());
        }
        TeamMember member = teamService.addMember(teamId, dto.userId(), role, userId);
        return ResponseEntity.status(201).body(toMemberDto(member));
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long teamId, @PathVariable Long memberId) {
        Long userId = requireUserId();
        findTeamOrThrow(teamId);
        teamService.removeMember(teamId, memberId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    public List<TeamMemberDTO> listMembers(@PathVariable Long teamId) {
        Long userId = requireUserId();
        authz.requireTeamMember(teamId, userId);
        return teamService.getTeamMembers(teamId).stream().map(this::toMemberDto).toList();
    }

    @PostMapping("/{teamId}/radars")
    public ResponseEntity<RadarSummaryDTO> generateTeamRadar(@PathVariable Long teamId) {
        Long userId = requireUserId();
        findTeamOrThrow(teamId);
        authz.requireTeamMember(teamId, userId);
        RadarSummaryDTO dto = teamRadarService.createTeamRadar(teamId, userId);
        return ResponseEntity.status(201).body(dto);
    }

    @GetMapping("/{teamId}/radars")
    public List<RadarSummaryDTO> listTeamRadars(@PathVariable Long teamId) {
        Long userId = requireUserId();
        authz.requireTeamMember(teamId, userId);
        List<TeamRadar> teamRadars = teamRadarService.getTeamRadars(teamId);
        return teamRadars.stream().map(tr -> {
            Radar r = radarRepo.findById(tr.getRadarId()).orElse(null);
            if (r == null) return null;
            return new RadarSummaryDTO(r.getId(), r.getStatus(), r.getPeriodStart(), r.getPeriodEnd(),
                r.getGeneratedAt(), r.getGenerationMs(), r.getTokenCount(), 0);
        }).filter(java.util.Objects::nonNull).toList();
    }

    private Long requireUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return userId;
    }

    private Team findTeamOrThrow(Long teamId) {
        return teamRepo.findById(teamId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    }

    private TeamDTO toDto(Team team) {
        int memberCount = (int) memberRepo.countByTeamId(team.getId());
        return new TeamDTO(team.getId(), team.getName(), team.getSlug(),
            team.getPlan().name(), team.getOwnerId(), memberCount);
    }

    private TeamMemberDTO toMemberDto(TeamMember m) {
        return new TeamMemberDTO(m.getUserId(), m.getRole().name(), m.getJoinedAt());
    }
}
