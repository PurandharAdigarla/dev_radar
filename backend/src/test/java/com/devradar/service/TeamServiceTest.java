package com.devradar.service;

import com.devradar.domain.Team;
import com.devradar.domain.TeamMember;
import com.devradar.domain.TeamPlan;
import com.devradar.domain.TeamRole;
import com.devradar.repository.TeamMemberRepository;
import com.devradar.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock TeamRepository teamRepo;
    @Mock TeamMemberRepository memberRepo;
    @Mock TeamAuthorizationService authz;
    @Mock PlanEnforcer planEnforcer;

    TeamService service;

    @BeforeEach
    void setUp() {
        service = new TeamService(teamRepo, memberRepo, authz, planEnforcer);
    }

    @Test
    void createTeam_savesTeamAndOwnerMember() {
        when(teamRepo.findBySlug(any())).thenReturn(Optional.empty());
        when(teamRepo.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(memberRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.createTeam("My Cool Team", 42L);

        assertEquals("My Cool Team", result.getName());
        assertEquals("my-cool-team", result.getSlug());
        assertEquals(42L, result.getOwnerId());
        assertEquals(TeamPlan.TEAM, result.getPlan());

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(memberRepo).save(memberCaptor.capture());
        TeamMember owner = memberCaptor.getValue();
        assertEquals(1L, owner.getTeamId());
        assertEquals(42L, owner.getUserId());
        assertEquals(TeamRole.OWNER, owner.getRole());
    }

    @Test
    void createTeam_appendsTimestamp_whenSlugExists() {
        when(teamRepo.findBySlug("my-team")).thenReturn(Optional.of(new Team()));
        when(teamRepo.save(any())).thenAnswer(inv -> {
            Team t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(memberRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Team result = service.createTeam("My Team", 1L);

        assertTrue(result.getSlug().startsWith("my-team-"));
        assertTrue(result.getSlug().length() > "my-team-".length());
    }

    @Test
    void addMember_requiresAdmin() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN))
            .when(authz).requireTeamAdmin(eq(1L), eq(99L));

        assertThrows(ResponseStatusException.class, () ->
            service.addMember(1L, 2L, TeamRole.MEMBER, 99L));
    }

    @Test
    void addMember_rejectsOwnerRole() {
        // authz passes
        doNothing().when(authz).requireTeamAdmin(1L, 10L);
        doNothing().when(planEnforcer).enforceTeamMemberLimit(1L);
        when(memberRepo.findByTeamIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.addMember(1L, 2L, TeamRole.OWNER, 10L));

        assertTrue(ex.getMessage().contains("OWNER"));
    }

    @Test
    void addMember_rejectsDuplicate() {
        doNothing().when(authz).requireTeamAdmin(1L, 10L);
        doNothing().when(planEnforcer).enforceTeamMemberLimit(1L);
        when(memberRepo.findByTeamIdAndUserId(1L, 2L)).thenReturn(Optional.of(new TeamMember()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.addMember(1L, 2L, TeamRole.MEMBER, 10L));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void removeMember_cannotRemoveOwner() {
        doNothing().when(authz).requireTeamAdmin(1L, 10L);
        TeamMember ownerMember = new TeamMember();
        ownerMember.setRole(TeamRole.OWNER);
        when(memberRepo.findByTeamIdAndUserId(1L, 5L)).thenReturn(Optional.of(ownerMember));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
            service.removeMember(1L, 5L, 10L));

        assertTrue(ex.getMessage().contains("owner"));
    }

    @Test
    void removeMember_removesRegularMember() {
        doNothing().when(authz).requireTeamAdmin(1L, 10L);
        TeamMember member = new TeamMember();
        member.setRole(TeamRole.MEMBER);
        when(memberRepo.findByTeamIdAndUserId(1L, 2L)).thenReturn(Optional.of(member));

        service.removeMember(1L, 2L, 10L);

        verify(memberRepo).deleteByTeamIdAndUserId(1L, 2L);
    }

    @Test
    void generateSlug_handlesSpecialCharacters() {
        assertEquals("hello-world", TeamService.generateSlug("Hello World"));
        assertEquals("cafe", TeamService.generateSlug("Café"));
        assertEquals("team-123", TeamService.generateSlug("  Team  123  "));
        assertEquals("team", TeamService.generateSlug("!!!"));
    }

    @Test
    void getTeamsForUser_returnsEmptyWhenNoMemberships() {
        when(memberRepo.findByUserId(99L)).thenReturn(List.of());
        List<Team> result = service.getTeamsForUser(99L);
        assertTrue(result.isEmpty());
    }
}
