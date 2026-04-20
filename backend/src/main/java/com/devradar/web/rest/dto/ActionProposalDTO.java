package com.devradar.web.rest.dto;
import com.devradar.domain.ActionProposalKind;
import com.devradar.domain.ActionProposalStatus;
import java.time.Instant;
public record ActionProposalDTO(
    Long id, Long radarId, ActionProposalKind kind, String payloadJson,
    ActionProposalStatus status, String prUrl, String failureReason,
    Instant createdAt, Instant updatedAt
) {}
