package com.trackingaggregator.service;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.dhl.DHLClient;
import com.trackingaggregator.entity.TrackingQuery;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Db;
import com.trackingaggregator.support.Fixtures;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The TrackingService branches that reach the persistence block, which needs a real
 * transaction manager and so cannot live in the plain-JUnit TrackingServiceTest.
 *
 * <p>Two deliberate choices:
 * <ul>
 *   <li>{@code @InjectSpy}, not {@code @InjectMock}. TrackingService's constructor builds
 *       {@code Collectors.toMap(CourierClient::getCourier, ...)} over every client; a
 *       full mock returns null from getCourier() and the map build dies with an NPE from
 *       HashMap.merge. A spy keeps the real getCourier()/isEnabled().</li>
 *   <li>No {@code @TestTransaction}. The service commits its analytics row in
 *       {@code QuarkusTransaction.requiringNew()}, which is independent of an enclosing
 *       test transaction - the row would survive the rollback and leak into the next
 *       test. Explicit cleanup instead.</li>
 * </ul>
 */
@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class TrackingServicePersistenceTest {

    private static final String TN = Fixtures.DHL_NUMBER;

    @Inject
    TrackingService trackingService;

    @InjectSpy
    DHLClient dhlClient;

    @BeforeEach
    void clean() {
        Db.clean();
        Mockito.reset(dhlClient);
    }

    /** All-UTC so cached and live results compare equal. */
    private static TrackingResult result(ShipmentStatus status) {
        return new TrackingResult()
                .trackingNumber(TN)
                .courier(Courier.DHL)
                .status(status)
                .events(List.of(Fixtures.event("2026-09-10T14:30:00Z", "Frankfurt",
                        "Processed at facility", ShipmentStatus.IN_TRANSIT)));
    }

    @Test
    @DisplayName("a successful lookup is cached and recorded")
    void successPathWritesCacheAndAnalytics() {
        Mockito.doReturn(Optional.of(result(ShipmentStatus.DELIVERED)))
                .when(dhlClient).track(TN);

        assertThat(trackingService.track(TN, Courier.DHL)).isPresent();

        assertThat(Db.cacheCount()).isEqualTo(1L);
        assertThat(Db.queryCount()).isEqualTo(1L);

        TrackingQuery query = Db.firstQuery();
        assertThat(query.trackingNumber).isEqualTo(TN);
        assertThat(query.courier).isEqualTo("DHL");
        assertThat(query.status).isEqualTo("DELIVERED");
        assertThat(query.queriedAt).isNotNull().isBefore(LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("a cache hit records no second analytics row")
    void cacheHitSkipsCourierAndAnalytics() {
        Mockito.doReturn(Optional.of(result(ShipmentStatus.DELIVERED)))
                .when(dhlClient).track(TN);

        trackingService.track(TN, Courier.DHL);
        trackingService.track(TN, Courier.DHL);

        Mockito.verify(dhlClient, Mockito.times(1)).track(TN);
        // Analytics therefore under-count real user lookups by exactly the cache hit
        // rate - probably not what a "tracking_query" table is meant to measure.
        assertThat(Db.queryCount()).isEqualTo(1L);
        assertThat(Db.cacheCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a shipment that is not found writes nothing")
    void emptyResultWritesNothing() {
        Mockito.doReturn(Optional.empty()).when(dhlClient).track(TN);

        assertThat(trackingService.track(TN, Courier.DHL)).isEmpty();

        assertThat(Db.cacheCount()).isZero();
        assertThat(Db.queryCount()).isZero();
    }

    @Test
    @DisplayName("a null status is normalized to UNKNOWN instead of NPEing")
    void nullStatusIsNormalizedToUnknown() {
        Mockito.doReturn(Optional.of(Fixtures.withNullStatus(TN, Courier.DHL)))
                .when(dhlClient).track(TN);

        Optional<TrackingResult> result = trackingService.track(TN, Courier.DHL);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getStatus()).isEqualTo(ShipmentStatus.UNKNOWN);

        assertThat(Db.cacheCount()).isEqualTo(1L);
        assertThat(Db.queryCount()).isEqualTo(1L);
        assertThat(Db.firstQuery().status).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("a courier outage serves the stale cache and records nothing")
    void courierFailureServesStaleCache() {
        String json = """
                {"trackingNumber":"%s","courier":"DHL","status":"IN_TRANSIT","events":[]}
                """.formatted(TN);
        Db.seedCache(TN, "DHL", json, "IN_TRANSIT", LocalDateTime.now().minusHours(2));

        Mockito.doThrow(new CourierApiException(Courier.DHL, TN, 500, new RuntimeException("upstream")))
                .when(dhlClient).track(TN);

        Optional<TrackingResult> result = trackingService.track(TN, Courier.DHL);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(Db.queryCount()).isZero();
    }

    @Test
    @DisplayName("a courier outage with nothing cached propagates")
    void courierFailureWithoutCachePropagates() {
        Mockito.doThrow(new CourierApiException(Courier.DHL, TN, 500, new RuntimeException("upstream")))
                .when(dhlClient).track(TN);

        assertThatThrownBy(() -> trackingService.track(TN, Courier.DHL))
                .isInstanceOf(CourierApiException.class);

        assertThat(Db.cacheCount()).isZero();
        assertThat(Db.queryCount()).isZero();
    }
}
