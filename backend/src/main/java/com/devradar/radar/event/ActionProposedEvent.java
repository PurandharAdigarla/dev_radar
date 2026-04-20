package com.devradar.radar.event;
public record ActionProposedEvent(Long radarId, Long proposalId, String kind, String payloadJson) {}
