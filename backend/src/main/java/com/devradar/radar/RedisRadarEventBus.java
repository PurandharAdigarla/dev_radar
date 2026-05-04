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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RedisRadarEventBus implements RadarEventBus {

    private static final Logger LOG = LoggerFactory.getLogger(RedisRadarEventBus.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final StringRedisTemplate redis;
    private final RedisMessageListenerContainer listenerContainer;

    private final ConcurrentHashMap<Long, List<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, MessageListener> listeners = new ConcurrentHashMap<>();

    public RedisRadarEventBus(StringRedisTemplate redis, RedisMessageListenerContainer listenerContainer) {
        this.redis = redis;
        this.listenerContainer = listenerContainer;
    }

    @Override
    public SseEmitter subscribe(Long radarId) {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.computeIfAbsent(radarId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(radarId, emitter));
        emitter.onTimeout(() -> removeEmitter(radarId, emitter));
        emitter.onError(e -> removeEmitter(radarId, emitter));

        listeners.computeIfAbsent(radarId, id -> {
            MessageListener listener = (message, pattern) -> onRedisMessage(id, message);
            listenerContainer.addMessageListener(listener, new ChannelTopic(channelName(id)));
            return listener;
        });

        return emitter;
    }

    @Override
    public void publishStarted(RadarStartedEvent event) { publish(event.radarId(), "radar.started", event); }

    @Override
    public void publishProgress(AgentProgressEvent event) { publish(event.radarId(), "agent.progress", event); }

    @Override
    public void publishThemeComplete(ThemeCompleteEvent event) { publish(event.radarId(), "theme.complete", event); }

    @Override
    public void publishComplete(RadarCompleteEvent event) { publish(event.radarId(), "radar.complete", event); }

    @Override
    public void publishFailed(RadarFailedEvent event) { publish(event.radarId(), "radar.failed", event); }

    private void publish(Long radarId, String eventName, Object data) {
        try {
            String payload = JSON.writeValueAsString(new RedisEnvelope(eventName, JSON.writeValueAsString(data)));
            redis.convertAndSend(channelName(radarId), payload);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize event {} for radar={}", eventName, radarId, e);
        }
    }

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
        MessageListener listener = listeners.remove(radarId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener, new ChannelTopic(channelName(radarId)));
        }
    }

    private void removeEmitter(Long radarId, SseEmitter emitter) {
        subscribers.computeIfPresent(radarId, (k, list) -> {
            list.remove(emitter);
            if (list.isEmpty()) {
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

    record RedisEnvelope(String eventName, String dataJson) {}
}
