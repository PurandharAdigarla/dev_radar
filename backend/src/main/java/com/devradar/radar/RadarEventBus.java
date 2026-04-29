package com.devradar.radar;

import com.devradar.radar.event.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface RadarEventBus {
    SseEmitter subscribe(Long radarId);
    void publishStarted(RadarStartedEvent event);
    void publishThemeComplete(ThemeCompleteEvent event);
    void publishComplete(RadarCompleteEvent event);
    void publishFailed(RadarFailedEvent event);
    void publishActionProposed(ActionProposedEvent event);
}
