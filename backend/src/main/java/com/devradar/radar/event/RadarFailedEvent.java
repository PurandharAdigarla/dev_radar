package com.devradar.radar.event;
public record RadarFailedEvent(Long radarId, String errorCode, String message) {}
