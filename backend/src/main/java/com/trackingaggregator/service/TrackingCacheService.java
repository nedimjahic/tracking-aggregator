package com.trackingaggregator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackingaggregator.entity.TrackingCache;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class TrackingCacheService {

    private static final Logger LOG = Logger.getLogger(TrackingCacheService.class.getName());

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "tracking.cache.ttl.delivered", defaultValue = "1440")
    int deliveredTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.in-transit", defaultValue = "30")
    int inTransitTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.out-for-delivery", defaultValue = "10")
    int outForDeliveryTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.failed-attempt", defaultValue = "30")
    int failedAttemptTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.picked-up", defaultValue = "60")
    int pickedUpTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.returned", defaultValue = "1440")
    int returnedTtlMinutes;

    @ConfigProperty(name = "tracking.cache.ttl.default", defaultValue = "15")
    int defaultTtlMinutes;

    public Optional<TrackingResult> get(String trackingNumber, Courier courier) {
        TrackingCache cached = TrackingCache.findByTrackingNumberAndCourier(trackingNumber, courier.toString());
        if (cached == null) {
            return Optional.empty();
        }

        Duration ttl = getTtl(cached.shipmentStatus);
        if (cached.cachedAt.plus(ttl).isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(cached.responseJson, TrackingResult.class));
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to deserialize cached tracking result", e);
            return Optional.empty();
        }
    }

    public Optional<TrackingResult> getStale(String trackingNumber, Courier courier) {
        TrackingCache cached = TrackingCache.findByTrackingNumberAndCourier(trackingNumber, courier.toString());
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached.responseJson, TrackingResult.class));
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to deserialize stale cached tracking result", e);
            return Optional.empty();
        }
    }

    @Transactional
    public void put(String trackingNumber, Courier courier, TrackingResult result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            String status = result.getStatus() != null ? result.getStatus().toString() : null;

            TrackingCache.getEntityManager().createNativeQuery(
                    "INSERT INTO tracking_cache (tracking_number, courier, response_json, cached_at, shipment_status) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5) " +
                    "ON CONFLICT (tracking_number, courier) DO UPDATE SET " +
                    "response_json = EXCLUDED.response_json, cached_at = EXCLUDED.cached_at, " +
                    "shipment_status = EXCLUDED.shipment_status")
                .setParameter(1, trackingNumber)
                .setParameter(2, courier.toString())
                .setParameter(3, json)
                .setParameter(4, LocalDateTime.now())
                .setParameter(5, status)
                .executeUpdate();
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Failed to serialize tracking result for caching", e);
        }
    }

    private Duration getTtl(String status) {
        if (status == null) return Duration.ofMinutes(defaultTtlMinutes);
        return switch (status) {
            case "DELIVERED" -> Duration.ofMinutes(deliveredTtlMinutes);
            case "IN_TRANSIT" -> Duration.ofMinutes(inTransitTtlMinutes);
            case "OUT_FOR_DELIVERY" -> Duration.ofMinutes(outForDeliveryTtlMinutes);
            case "FAILED_ATTEMPT" -> Duration.ofMinutes(failedAttemptTtlMinutes);
            case "PICKED_UP" -> Duration.ofMinutes(pickedUpTtlMinutes);
            case "RETURNED" -> Duration.ofMinutes(returnedTtlMinutes);
            default -> Duration.ofMinutes(defaultTtlMinutes);
        };
    }
}
