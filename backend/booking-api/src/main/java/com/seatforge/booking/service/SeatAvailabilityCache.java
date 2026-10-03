package com.seatforge.booking.service;

import com.seatforge.booking.web.dto.EventSummaryResponse;
import com.seatforge.booking.web.dto.SeatResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private static final Duration BASE_TTL = Duration.ofSeconds(60);
    private static final Duration JITTER_MAX = Duration.ofSeconds(15);

    private Duration ttlWithJitter() {
        long jitterMillis = java.util.concurrent.ThreadLocalRandom.current()
                .nextLong(JITTER_MAX.toMillis() + 1);
        return BASE_TTL.plusMillis(jitterMillis);
    }

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter cacheInvalidations;
    private final Counter cacheWrites;
    private final Counter summaryHits;
    private final Counter summaryMisses;

    public SeatAvailabilityCache(StringRedisTemplate redis,
                                 JsonMapper jsonMapper,
                                 MeterRegistry meterRegistry) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.cacheHits = meterRegistry.counter("seat.cache.hits");
        this.cacheMisses = meterRegistry.counter("seat.cache.misses");
        this.cacheInvalidations = meterRegistry.counter("seat.cache.invalidations");
        this.cacheWrites = meterRegistry.counter("seat.cache.writes");
        this.summaryHits = meterRegistry.counter("seat.cache.summary.hits");
        this.summaryMisses = meterRegistry.counter("seat.cache.summary.misses");
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
            redis.opsForValue().set(key(eventId), json, ttlWithJitter());
            cacheWrites.increment();
        } catch (Exception e) {
            log.warn("Cache serialization failed for event {}, skipping cache", eventId, e);
        }
    }

    public void invalidate(Long eventId) {
        redis.delete(key(eventId));
        cacheInvalidations.increment();
    }

    public void recordHit() {
        cacheHits.increment();
    }

    public void recordMiss() {
        cacheMisses.increment();
    }

    private String key(Long eventId) {
        return "seatlist:event:" + eventId;
    }

    public Optional<EventSummaryResponse> getSummary(Long eventId) {
        String key = summaryKey(eventId);
        String json = redis.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            EventSummaryResponse summary = jsonMapper.readValue(json, EventSummaryResponse.class);
            return Optional.of(summary);
        } catch (Exception e) {
            log.warn("Summary cache deserialization failed for event {}, evicting key", eventId, e);
            redis.delete(key);
            return Optional.empty();
        }
    }

    public void putSummary(Long eventId, EventSummaryResponse summary) {
        try {
            String json = jsonMapper.writeValueAsString(summary);
            redis.opsForValue().set(summaryKey(eventId), json, ttlWithJitter());
            cacheWrites.increment();
        } catch (Exception e) {
            log.warn("Summary cache serialization failed for event {}, skipping cache", eventId, e);
        }
    }

    public void invalidateSummary(Long eventId) {
        redis.delete(summaryKey(eventId));
        cacheInvalidations.increment();
    }

    private String summaryKey(Long eventId) {
        return "seatsummary:event:" + eventId;
    }

    public void recordSummaryHit() {
        summaryHits.increment();
    }
    public void recordSummaryMiss() {
        summaryMisses.increment();
    }

}