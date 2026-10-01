package com.seatforge.booking.service;

import com.seatforge.booking.web.dto.SeatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class SeatAvailabilityCache {

    private static final Logger log = LoggerFactory.getLogger(SeatAvailabilityCache.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public SeatAvailabilityCache(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    public Optional<List<SeatResponse>> get(Long eventId) {
        String key = key(eventId);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            List<SeatResponse> seats = jsonMapper.readValue(
                    json,
                    jsonMapper.getTypeFactory().constructCollectionType(List.class, SeatResponse.class));
            return Optional.of(seats);
        } catch (Exception e) {
            log.warn("Cache deserialization failed for event {}, evicting key", eventId, e);
            redis.delete(key);
            return Optional.empty();
        }
    }

    public void put(Long eventId, List<SeatResponse> seats) {
        try {
            String json = jsonMapper.writeValueAsString(seats);
            redis.opsForValue().set(key(eventId), json, TTL);
        } catch (Exception e) {
            log.warn("Cache serialization failed for event {}, skipping cache", eventId, e);
        }
    }

    public void invalidate(Long eventId) {
        redis.delete(key(eventId));
    }

    private String key(Long eventId) {
        return "seatlist:event:" + eventId;
    }
}