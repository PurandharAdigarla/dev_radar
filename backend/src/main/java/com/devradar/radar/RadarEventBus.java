package com.devradar.radar;

import com.devradar.radar.event.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis-backed event bus for radar SSE events. Publishes events to a Redis channel so that
 * any instance in a multi-instance Cloud Run deployment can deliver SSE events to connected clients.
 *
 * <p>Each instance subscribes to radar channels for its locally-connected SseEmitters. The publish
 * side broadcasts to Redis, which fans out to all subscribing instances.</p>
 */
@Component
public class RadarEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(RadarEventBus.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;

    /** Local emitters on this instance, keyed by radarId. */
    private final ConcurrentHashMap<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    /** Track Redis subscriptions so we don't double-subscribe. */
    private final ConcurrentHashMap<Long, MessageListener> listeners = new ConcurrentHashMap<>();

    public RadarEventBus(StringRedisTemplate redis, RedisMessageListenerContainer listenerContainer) {
        this.redis = redis;
        this.listenerContainer = listenerContainer;
    }

    public SseEmitter subscribe(Long radarId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(radarId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(radarId, emitter));
        emitter.onTimeout(() -> removeEmitter(radarId, emitter));
        emitter.onError(e -> removeEmitter(radarId, emitter));

        // Ensure this instance is subscribed to the Redis channel for this radarId
        listeners.computeIfAbsent(radarId, id -> {
            MessageListener listener = (message, pattern) -> onRedisMessage(id, message);
            listenerContainer.addMessageListener(listener, new ChannelTopic(channelName(id)));
            return listener;
        });

        return emitter;
    }

    // ── publish methods (write to Redis, fan out to all instances) ──

    public void publishStarted(RadarStartedEvent event) { publish(event.radarId(), "radar.started", event); }
    public void publishThemeComplete(ThemeCompleteEvent event) { publish(event.radarId(), "theme.complete", event); }
    public void publishComplete(RadarCompleteEvent event) { publish(event.radarId(), "radar.complete", event); }
    public void publishFailed(RadarFailedEvent event) { publish(event.radarId(), "radar.failed", event); }
    public void publishActionProposed(ActionProposedEvent event) { publish(event.radarId(), "action.proposed", event); }

    // ── internals ──

    private void publish(Long radarId, String eventName, Object data) {
        try {
            String payload = JSON.writeValueAsString(new RedisEnvelope(eventName, JSON.writeValueAsString(data)));
            redis.convertAndSend(channelName(radarId), payload);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize event {} for radar={}", eventName, radarId, e);
        }
    }

    /** Called on every instance that has a Redis subscription for this radarId. */
    private void onRedisMessage(Long radarId, Message message) {
        List<SseEmitter> list = subscribers.get(radarId);
        if (list == null || list.isEmpty()) return;

        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RedisEnvelope envelope = JSON.readValue(body, RedisEnvelope.class);
            boolean terminal = "radar.complete".equals(envelope.eventName())
                    || "radar.failed".equals(envelope.eventName());

            for (SseEmitter e : list) {
                try {
                    e.send(SseEmitter.event().name(envelope.eventName()).data(envelope.dataJson()));
                } catch (IOException ex) {
                    LOG.debug("subscriber dropped for radar={}: {}", radarId, ex.toString());
                    removeEmitter(radarId, e);
                }
            }

            if (terminal) {
                completeAll(radarId);
            }
        } catch (Exception e) {
            LOG.error("Failed to process Redis message for radar={}", radarId, e);
        }
    }

    private void completeAll(Long radarId) {
        List<SseEmitter> list = subscribers.remove(radarId);
        if (list != null) {
            for (SseEmitter e : list) {
                try { e.complete(); } catch (Exception ignored) {}
            }
        }
        // Unsubscribe from Redis channel
        MessageListener listener = listeners.remove(radarId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener, new ChannelTopic(channelName(radarId)));
        }
    }

    private void removeEmitter(Long radarId, SseEmitter emitter) {
        subscribers.computeIfPresent(radarId, (k, list) -> {
            list.remove(emitter);
            if (list.isEmpty()) {
                // Last emitter on this instance — unsubscribe from Redis
                MessageListener listener = listeners.remove(radarId);
                if (listener != null) {
                    listenerContainer.removeMessageListener(listener, new ChannelTopic(channelName(radarId)));
                }
                return null;
            }
            return list;
        });
    }

    private static String channelName(Long radarId) {
        return "radar:events:" + radarId;
    }

    /** Envelope for the Redis message: event name + serialized data JSON. */
    record RedisEnvelope(String eventName, String dataJson) {}
}
