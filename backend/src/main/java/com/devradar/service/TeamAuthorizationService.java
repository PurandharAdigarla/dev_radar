package com.devradar.service;

import com.devradar.domain.TeamMember;
import com.devradar.domain.TeamRole;
import com.devradar.repository.TeamMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class TeamAuthorizationService {

    private final TeamMemberRepository memberRepo;

    public TeamAuthorizationService(TeamMemberRepository memberRepo) {
        this.memberRepo = memberRepo;
    }

    public boolean isTeamMember(Long teamId, Long userId) {
        return memberRepo.findByTeamIdAndUserId(teamId, userId).isPresent();
    }

    public boolean isTeamAdmin(Long teamId, Long userId) {
        Optional<TeamMember> member = memberRepo.findByTeamIdAndUserId(teamId, userId);
        return member.isPresent() &&
            (member.get().getRole() == TeamRole.OWNER || member.get().getRole() == TeamRole.ADMIN);
    }

    public void requireTeamMember(Long teamId, Long userId) {
        if (!isTeamMember(teamId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this team");
        }
    }

    public void requireTeamAdmin(Long teamId, Long userId) {
        if (!isTeamAdmin(teamId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requires team admin privileges");
        }
    }
}
