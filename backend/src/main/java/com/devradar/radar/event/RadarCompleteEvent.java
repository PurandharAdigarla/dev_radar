package com.devradar.radar.event;
public record RadarCompleteEvent(Long radarId, long generationMs, int tokenCount) {}
