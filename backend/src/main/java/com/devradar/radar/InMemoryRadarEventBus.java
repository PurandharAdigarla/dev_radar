package com.devradar.radar;

import com.devradar.radar.event.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnMissingBean(RedisRadarEventBus.class)
public class InMemoryRadarEventBus implements RadarEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryRadarEventBus.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConcurrentHashMap<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long radarId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(radarId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(radarId, emitter));
        emitter.onTimeout(() -> removeEmitter(radarId, emitter));
        emitter.onError(e -> removeEmitter(radarId, emitter));
        return emitter;
    }

    @Override
    public void publishStarted(RadarStartedEvent event) { send(event.radarId(), "radar.started", event, false); }

    @Override
    public void publishThemeComplete(ThemeCompleteEvent event) { send(event.radarId(), "theme.complete", event, false); }

    @Override
    public void publishComplete(RadarCompleteEvent event) { send(event.radarId(), "radar.complete", event, true); }

    @Override
    public void publishFailed(RadarFailedEvent event) { send(event.radarId(), "radar.failed", event, true); }

    @Override
    public void publishActionProposed(ActionProposedEvent event) { send(event.radarId(), "action.proposed", event, false); }

    private void send(Long radarId, String eventName, Object data, boolean terminal) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list == null || list.isEmpty()) return;

        String json;
        try {
            json = JSON.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize event {} for radar={}", eventName, radarId, e);
            return;
        }

        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(eventName).data(json));
            } catch (IOException ex) {
                LOG.debug("subscriber dropped for radar={}: {}", radarId, ex.toString());
                removeEmitter(radarId, e);
            }
        }

        if (terminal) {
            completeAll(radarId);
        }
    }

    private void completeAll(Long radarId) {
        List<SseEmitter> list = subscribers.remove(radarId);
        if (list != null) {
            for (SseEmitter e : list) {
                try { e.complete(); } catch (Exception ignored) {}
            }
        }
    }

    private void removeEmitter(Long radarId, SseEmitter emitter) {
        subscribers.computeIfPresent(radarId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
