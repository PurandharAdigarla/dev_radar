package com.devradar.service;

import com.devradar.domain.Team;
import com.devradar.domain.TeamMember;
import com.devradar.domain.TeamRole;
import com.devradar.plan.PlanEnforcementService;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class TeamService {

    private static final Pattern NON_ALPHANUM = Pattern.compile("[^a-z0-9-]");
    private static final Pattern MULTI_DASH = Pattern.compile("-{2,}");

    private final TeamRepository teamRepo;
    private final TeamMemberRepository memberRepo;
    private final TeamAuthorizationService authz;
    private final PlanEnforcer planEnforcer;
    private final PlanEnforcementService planEnforcement;

    public TeamService(TeamRepository teamRepo, TeamMemberRepository memberRepo,
                       TeamAuthorizationService authz, PlanEnforcer planEnforcer,
                       PlanEnforcementService planEnforcement) {
        this.teamRepo = teamRepo;
        this.memberRepo = memberRepo;
        this.authz = authz;
        this.planEnforcer = planEnforcer;
        this.planEnforcement = planEnforcement;
    }

    @Transactional
    public Team createTeam(String name, Long ownerId) {
        planEnforcement.checkTeamAccess(ownerId);
        String slug = generateSlug(name);
        if (teamRepo.findBySlug(slug).isPresent()) {
            slug = slug + "-" + System.currentTimeMillis();
        }
        Team team = new Team();
        team.setName(name);
        team.setSlug(slug);
        team.setOwnerId(ownerId);
        team = teamRepo.save(team);

        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(ownerId);
        owner.setRole(TeamRole.OWNER);
        memberRepo.save(owner);

        return team;
    }

    @Transactional
    public TeamMember addMember(Long teamId, Long userId, TeamRole role, Long requesterId) {
        authz.requireTeamAdmin(teamId, requesterId);
        planEnforcer.enforceTeamMemberLimit(teamId);

        if (memberRepo.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a team member");
        }
        if (role == TeamRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign OWNER role via add member");
        }

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setRole(role);
        return memberRepo.save(member);
    }

    @Transactional
    public void removeMember(Long teamId, Long userId, Long requesterId) {
        authz.requireTeamAdmin(teamId, requesterId);

        TeamMember member = memberRepo.findByTeamIdAndUserId(teamId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (member.getRole() == TeamRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove the team owner");
        }
        memberRepo.deleteByTeamIdAndUserId(teamId, userId);
    }

    public List<TeamMember> getTeamMembers(Long teamId) {
        return memberRepo.findByTeamId(teamId);
    }

    public List<Team> getTeamsForUser(Long userId) {
        List<Long> teamIds = memberRepo.findByUserId(userId).stream()
            .map(TeamMember::getTeamId)
            .toList();
        if (teamIds.isEmpty()) return List.of();
        return teamRepo.findAllById(teamIds);
    }

    static String generateSlug(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        String slug = NON_ALPHANUM.matcher(normalized.toLowerCase(Locale.ROOT).replace(' ', '-')).replaceAll("");
        slug = MULTI_DASH.matcher(slug).replaceAll("-");
        slug = slug.replaceAll("^-|-$", "");
        if (slug.isEmpty()) slug = "team";
        if (slug.length() > 80) slug = slug.substring(0, 80);
        return slug;
    }
}
