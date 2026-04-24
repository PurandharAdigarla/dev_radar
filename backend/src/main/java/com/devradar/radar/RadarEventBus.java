package com.devradar.radar;

import com.devradar.radar.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory event bus: per-radar list of SseEmitters. Events are pushed synchronously to subscribers. */
@Component
public class RadarEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(RadarEventBus.class);

    private final ConcurrentHashMap<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long radarId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(radarId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(radarId, emitter));
        emitter.onTimeout(() -> remove(radarId, emitter));
        emitter.onError(e -> remove(radarId, emitter));
        return emitter;
    }

    public void publishStarted(RadarStartedEvent event) { send(event.radarId(), "radar.started", event); }
    public void publishThemeComplete(ThemeCompleteEvent event) { send(event.radarId(), "theme.complete", event); }
    public void publishComplete(RadarCompleteEvent event) {
        send(event.radarId(), "radar.complete", event);
        completeAll(event.radarId());
    }
    public void publishFailed(RadarFailedEvent event) {
        send(event.radarId(), "radar.failed", event);
        completeAll(event.radarId());
    }
    public void publishActionProposed(ActionProposedEvent event) { send(event.radarId(), "action.proposed", event); }

    private void send(Long radarId, String eventName, Object data) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try {
                e.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException ex) {
                LOG.debug("subscriber dropped for radar={}: {}", radarId, ex.toString());
                remove(radarId, e);
            }
        }
    }

    private void completeAll(Long radarId) {
        List<SseEmitter> list = subscribers.remove(radarId);
        if (list == null) return;
        for (SseEmitter e : list) {
            try { e.complete(); } catch (Exception ignored) {}
        }
    }

    private void remove(Long radarId, SseEmitter emitter) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list != null) list.remove(emitter);
    }
}
