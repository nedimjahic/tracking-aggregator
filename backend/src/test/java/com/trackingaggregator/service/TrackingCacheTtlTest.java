package com.trackingaggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trackingaggregator.entity.TrackingCache;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * The TTL matrix, exercised without a database.
 *
 * <p>{@code mockStatic} is not a stylistic choice here: during surefire the classes in
 * {@code target/classes} have not been Panache-enhanced yet (the quarkus-maven-plugin
 * build goal runs at {@code package}, after {@code test}), so calling
 * {@code TrackingCache.find(...)} for real would throw
 * "This method is normally automatically overridden in subclasses". The static mock
 * intercepts before the unenhanced body runs.
 *
 * <p>The {@code @ConfigProperty} fields are package-private plain ints, so this test lives
 * in the same package and assigns them directly rather than booting CDI. Making those
 * fields private would break this test.
 */
class TrackingCacheTtlTest {

    private static final String TN = Fixtures.DHL_NUMBER;

    private TrackingCacheService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        service = new TrackingCacheService();
        service.objectMapper = objectMapper;
        service.deliveredTtlMinutes = 1440;
        service.inTransitTtlMinutes = 30;
        service.outForDeliveryTtlMinutes = 10;
        service.failedAttemptTtlMinutes = 30;
        service.pickedUpTtlMinutes = 60;
        service.returnedTtlMinutes = 1440;
        service.defaultTtlMinutes = 15;
    }

    private TrackingCache row(String shipmentStatus, int ageMinutes) {
        TrackingCache row = new TrackingCache();
        row.trackingNumber = TN;
        row.courier = Courier.DHL.toString();
        row.shipmentStatus = shipmentStatus;
        row.cachedAt = LocalDateTime.now().minusMinutes(ageMinutes);
        row.responseJson = json(Fixtures.delivered(TN, Courier.DHL));
        return row;
    }

    private String json(TrackingResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Optional<TrackingResult> getWith(TrackingCache stored) {
        try (MockedStatic<TrackingCache> tc = mockStatic(TrackingCache.class)) {
            tc.when(() -> TrackingCache.findByTrackingNumberAndCourier(TN, "DHL")).thenReturn(stored);
            return service.get(TN, Courier.DHL);
        }
    }

    private Optional<TrackingResult> getStaleWith(TrackingCache stored) {
        try (MockedStatic<TrackingCache> tc = mockStatic(TrackingCache.class)) {
            tc.when(() -> TrackingCache.findByTrackingNumberAndCourier(TN, "DHL")).thenReturn(stored);
            return service.getStale(TN, Courier.DHL);
        }
    }

    // The boundary itself (cachedAt + ttl == now) is inclusive-fresh for exactly one
    // instant, so it is deliberately not asserted - these stay a minute either side.
    @ParameterizedTest(name = "{0} is fresh at {1}m")
    @CsvSource({
            "IN_TRANSIT, 29",
            "OUT_FOR_DELIVERY, 9",
            "DELIVERED, 1439",
            "FAILED_ATTEMPT, 29",
            "PICKED_UP, 59",
            "RETURNED, 1439",
    })
    void freshEntriesAreReturned(String status, int ageMinutes) {
        assertThat(getWith(row(status, ageMinutes))).isPresent();
    }

    @ParameterizedTest(name = "{0} is stale at {1}m")
    @CsvSource({
            "IN_TRANSIT, 31",
            "OUT_FOR_DELIVERY, 11",
            "DELIVERED, 1441",
            "FAILED_ATTEMPT, 31",
            "PICKED_UP, 61",
            "RETURNED, 1441",
    })
    void expiredEntriesAreNotReturned(String status, int ageMinutes) {
        assertThat(getWith(row(status, ageMinutes))).isEmpty();
    }

    @Test
    @DisplayName("a null status falls back to the default TTL")
    void nullStatusUsesDefaultTtl() {
        assertThat(getWith(row(null, 14))).isPresent();
        assertThat(getWith(row(null, 16))).isEmpty();
    }

    @Test
    @DisplayName("a status outside the switch falls back to the default TTL")
    void unknownStatusUsesDefaultTtl() {
        assertThat(getWith(row("WEIRD", 14))).isPresent();
        assertThat(getWith(row("WEIRD", 16))).isEmpty();
    }

    @Test
    void missingRowReturnsEmpty() {
        assertThat(getWith(null)).isEmpty();
    }

    @Test
    @DisplayName("corrupt cached JSON is swallowed, not propagated")
    void corruptJsonReturnsEmpty() {
        TrackingCache stored = row(ShipmentStatus.IN_TRANSIT.toString(), 5);
        stored.responseJson = "{not json";

        assertThat(getWith(stored)).isEmpty();
    }

    @Test
    @DisplayName("the cache round trip preserves the instant but normalises the offset to UTC")
    void roundTripPreservesInstantButNotOffset() {
        TrackingResult original = Fixtures.delivered(TN, Courier.DHL);
        TrackingCache stored = row(ShipmentStatus.DELIVERED.toString(), 5);
        stored.responseJson = json(original);

        TrackingResult restored = getWith(stored).orElseThrow();

        assertThat(restored.getTrackingNumber()).isEqualTo(original.getTrackingNumber());
        assertThat(restored.getCourier()).isEqualTo(original.getCourier());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
        assertThat(restored.getEstimatedDelivery()).isEqualTo(original.getEstimatedDelivery());
        assertThat(restored.getEvents()).hasSize(2);

        var restoredEvent = restored.getEvents().getFirst();
        var originalEvent = original.getEvents().getFirst();

        // Jackson's ADJUST_DATES_TO_CONTEXT_TIME_ZONE (on by default) rewrites +02:00
        // into the equivalent UTC instant when reading back. The moment in time survives;
        // the original offset does not. Consequence: a cache HIT serves
        // "2026-09-10T12:30Z" where the cache MISS that populated it served
        // "2026-09-10T14:30+02:00" - the same instant, but a different string on the
        // wire, so a client rendering the raw offset without converting will show a
        // different clock time depending on whether the response was cached.
        assertThat(restoredEvent.getTimestamp().toInstant())
                .isEqualTo(originalEvent.getTimestamp().toInstant());
        assertThat(restoredEvent.getTimestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(restoredEvent.getTimestamp().getOffset())
                .isNotEqualTo(originalEvent.getTimestamp().getOffset());

        assertThat(restoredEvent.getLocation()).isEqualTo(originalEvent.getLocation());
        assertThat(restoredEvent.getDescription()).isEqualTo(originalEvent.getDescription());
        assertThat(restoredEvent.getStatus()).isEqualTo(originalEvent.getStatus());
    }

    @Test
    @DisplayName("getStale ignores the TTL entirely")
    void staleLookupIgnoresTtl() {
        assertThat(getStaleWith(row(ShipmentStatus.IN_TRANSIT.toString(), 10_000))).isPresent();
    }

    @Test
    void staleLookupOnMissingRowReturnsEmpty() {
        assertThat(getStaleWith(null)).isEmpty();
    }

    @Test
    void staleLookupWithCorruptJsonReturnsEmpty() {
        TrackingCache stored = row(ShipmentStatus.IN_TRANSIT.toString(), 10_000);
        stored.responseJson = "{not json";

        assertThat(getStaleWith(stored)).isEmpty();
    }
}
