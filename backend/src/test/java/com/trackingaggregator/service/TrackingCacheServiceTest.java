package com.trackingaggregator.service;

import com.trackingaggregator.entity.TrackingCache;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Db;
import com.trackingaggregator.support.Fixtures;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises TrackingCacheService against a real Postgres.
 *
 * <p>Postgres is not optional here: {@code put} is a native
 * {@code INSERT ... ON CONFLICT ... DO UPDATE} using {@code EXCLUDED.*}, which no
 * in-memory database implements. Panache statics also need the Quarkus augmentation that
 * only {@code @QuarkusTest} provides.
 */
@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class TrackingCacheServiceTest {

    private static final String TN = Fixtures.DHL_NUMBER;

    @Inject
    TrackingCacheService cacheService;

    @BeforeEach
    void clean() {
        Db.clean();
    }

    /** All-UTC so the result survives a JSON round trip under value equality. */
    private static TrackingResult utcResult(ShipmentStatus status) {
        return new TrackingResult()
                .trackingNumber(TN)
                .courier(Courier.DHL)
                .status(status)
                .estimatedDelivery(LocalDate.of(2026, 9, 15))
                .events(List.of(Fixtures.event("2026-09-10T14:30:00Z", "Frankfurt",
                        "Processed at facility", ShipmentStatus.IN_TRANSIT)));
    }

    @Test
    @DisplayName("a stored result round trips intact")
    void putThenGetReturnsAnEquivalentResult() {
        TrackingResult original = utcResult(ShipmentStatus.DELIVERED);

        cacheService.put(TN, Courier.DHL, original);

        assertThat(cacheService.get(TN, Courier.DHL)).contains(original);
    }

    @Test
    @DisplayName("the real Quarkus ObjectMapper also normalises offsets to UTC")
    void roundTripNormalisesNonUtcOffsets() {
        // Confirms with the injected, Quarkus-configured ObjectMapper what
        // TrackingCacheTtlTest showed with a hand-built one: a +02:00 timestamp comes
        // back as the equivalent UTC instant. Same moment, different wire format between
        // a cache miss and a cache hit.
        TrackingResult original = Fixtures.delivered(TN, Courier.DHL);

        cacheService.put(TN, Courier.DHL, original);
        TrackingResult restored = cacheService.get(TN, Courier.DHL).orElseThrow();

        TrackingEvent restoredEvent = restored.getEvents().getFirst();
        TrackingEvent originalEvent = original.getEvents().getFirst();

        assertThat(restoredEvent.getTimestamp().toInstant())
                .isEqualTo(originalEvent.getTimestamp().toInstant());
        assertThat(restoredEvent.getTimestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(restoredEvent.getTimestamp().getOffset())
                .isNotEqualTo(originalEvent.getTimestamp().getOffset());
    }

    @Test
    @DisplayName("a second put for the same key updates in place via ON CONFLICT")
    void putIsAnUpsert() {
        // The only test covering the native ON CONFLICT statement. Without it, dropping
        // the UNIQUE constraint or renaming a column breaks production silently.
        cacheService.put(TN, Courier.DHL, utcResult(ShipmentStatus.IN_TRANSIT));
        cacheService.put(TN, Courier.DHL, utcResult(ShipmentStatus.DELIVERED));

        assertThat(Db.cacheCount()).isEqualTo(1L);

        TrackingCache row = Db.findCache(TN, "DHL");
        assertThat(row.shipmentStatus).isEqualTo("DELIVERED");
        assertThat(row.responseJson).contains("DELIVERED");
        assertThat(cacheService.get(TN, Courier.DHL).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.DELIVERED);
    }

    @Test
    @DisplayName("the same tracking number under two couriers is two rows")
    void differentCouriersAreSeparateRows() {
        cacheService.put(TN, Courier.DHL, utcResult(ShipmentStatus.IN_TRANSIT));
        cacheService.put(TN, Courier.UPS, new TrackingResult()
                .trackingNumber(TN).courier(Courier.UPS)
                .status(ShipmentStatus.DELIVERED).events(List.of()));

        assertThat(Db.cacheCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a null status is stored as SQL NULL, not rejected")
    void nullStatusIsStoredAsNull() {
        cacheService.put(TN, Courier.DHL, Fixtures.withNullStatus(TN, Courier.DHL));

        assertThat(Db.cacheCount()).isEqualTo(1L);
        assertThat(Db.findCache(TN, "DHL").shipmentStatus).isNull();
    }

    @Test
    @DisplayName("a large payload fits - response_json is TEXT, not VARCHAR")
    void largePayloadIsStored() {
        String longDescription = "x".repeat(40_000);
        TrackingResult big = new TrackingResult()
                .trackingNumber(TN)
                .courier(Courier.DHL)
                .status(ShipmentStatus.IN_TRANSIT)
                .events(List.of(Fixtures.event("2026-09-10T14:30:00Z", "Frankfurt",
                        longDescription, ShipmentStatus.IN_TRANSIT)));

        cacheService.put(TN, Courier.DHL, big);

        assertThat(cacheService.get(TN, Courier.DHL).orElseThrow()
                .getEvents().getFirst().getDescription()).hasSize(40_000);
    }

    @Test
    @DisplayName("an expired row is invisible to get but still visible to getStale")
    void expiredRowIsOnlyVisibleAsStale() {
        String json = """
                {"trackingNumber":"%s","courier":"DHL","status":"IN_TRANSIT","events":[]}
                """.formatted(TN);
        Db.seedCache(TN, "DHL", json, "IN_TRANSIT", LocalDateTime.now().minusMinutes(31));

        assertThat(cacheService.get(TN, Courier.DHL)).isEmpty();
        assertThat(cacheService.getStale(TN, Courier.DHL)).isPresent();
    }

    @Test
    void corruptStoredJsonIsSwallowed() {
        Db.seedCache(TN, "DHL", "{", "IN_TRANSIT", LocalDateTime.now());

        assertThat(cacheService.get(TN, Courier.DHL)).isEmpty();
        assertThat(cacheService.getStale(TN, Courier.DHL)).isEmpty();
    }

    @Test
    void unknownTrackingNumberIsEmpty() {
        assertThat(cacheService.get("9999999999", Courier.DHL)).isEmpty();
    }

    @Test
    @DisplayName("the courier is part of the key")
    void courierMismatchIsAMiss() {
        cacheService.put(TN, Courier.DHL, utcResult(ShipmentStatus.DELIVERED));

        assertThat(cacheService.get(TN, Courier.UPS)).isEmpty();
    }

    @Test
    @DisplayName("concurrent writes for one key still leave a single row")
    void concurrentPutsDoNotViolateTheUniqueConstraint() throws Exception {
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        cacheService.put(TN, Courier.DHL, utcResult(ShipmentStatus.IN_TRANSIT));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failure.get()).isNull();
        assertThat(Db.cacheCount()).isEqualTo(1L);
        assertThat(cacheService.get(TN, Courier.DHL)).isPresent();
    }

    @Test
    void getOnAnEmptyTableIsEmpty() {
        Optional<TrackingResult> result = cacheService.get(TN, Courier.DHL);

        assertThat(result).isEmpty();
        assertThat(Db.cacheCount()).isZero();
    }
}
