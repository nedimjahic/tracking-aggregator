package com.trackingaggregator.service;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.support.Fixtures;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers every TrackingService branch that does not reach the persistence block.
 *
 * <p>The success path and the null-status case run inside
 * {@code QuarkusTransaction.requiringNew()}, which needs a real transaction manager, so
 * they live in {@code TrackingServicePersistenceTest} instead.
 */
@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    private static final String TN = Fixtures.DHL_NUMBER;

    @Mock
    CourierDetectorService detector;
    @Mock
    TrackingCacheService cache;
    @Mock
    CourierClient dhlClient;

    TrackingService service;

    @BeforeEach
    void setUp() {
        // The constructor only calls clientInstances.stream(), so a stubbed stream is
        // enough. A Stream is single-use, hence a fresh one per test.
        lenient().when(dhlClient.getCourier()).thenReturn(Courier.DHL);
        lenient().when(dhlClient.isEnabled()).thenReturn(true);

        @SuppressWarnings("unchecked")
        Instance<CourierClient> instances = mock(Instance.class);
        when(instances.stream()).thenReturn(Stream.of(dhlClient));

        service = new TrackingService(detector, cache, instances);
    }

    @Test
    @DisplayName("two clients registered for the same courier fail fast with a clear message")
    void duplicateClientsForTheSameCourierFailFast() {
        CourierClient secondDhlClient = mock(CourierClient.class);
        lenient().when(secondDhlClient.getCourier()).thenReturn(Courier.DHL);

        @SuppressWarnings("unchecked")
        Instance<CourierClient> instances = mock(Instance.class);
        when(instances.stream()).thenReturn(Stream.of(dhlClient, secondDhlClient));

        assertThatThrownBy(() -> new TrackingService(detector, cache, instances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DHL")
                .hasMessageContaining(dhlClient.getClass().getName())
                .hasMessageContaining(secondDhlClient.getClass().getName());
    }

    @Test
    void detectCourierDelegatesToTheDetector() {
        when(detector.detect(TN)).thenReturn(Optional.of(Courier.DHL));

        assertThat(service.detectCourier(TN)).contains(Courier.DHL);
        verify(detector).detect(TN);
    }

    @Test
    @DisplayName("a courier with no registered client returns empty without touching the cache")
    void unregisteredCourierReturnsEmpty() {
        assertThat(service.track(TN, Courier.UPS)).isEmpty();
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("a disabled client is never called")
    void disabledClientReturnsEmpty() {
        when(dhlClient.isEnabled()).thenReturn(false);

        assertThat(service.track(TN, Courier.DHL)).isEmpty();

        verify(dhlClient, never()).track(any());
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("a cache hit short-circuits the courier call")
    void cacheHitSkipsTheCourier() {
        TrackingResult cached = Fixtures.delivered(TN, Courier.DHL);
        when(cache.get(TN, Courier.DHL)).thenReturn(Optional.of(cached));

        assertThat(service.track(TN, Courier.DHL)).containsSame(cached);

        verify(dhlClient, never()).track(any());
        // Note: the cache-hit path also skips the TrackingQuery analytics insert, so
        // analytics only ever record cache MISSES. Asserted end-to-end in
        // TrackingServicePersistenceTest.
    }

    @Test
    @DisplayName("a courier returning no shipment is not cached")
    void emptyCourierResultIsNotCached() {
        when(cache.get(TN, Courier.DHL)).thenReturn(Optional.empty());
        when(dhlClient.track(TN)).thenReturn(Optional.empty());

        assertThat(service.track(TN, Courier.DHL)).isEmpty();

        verify(cache, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("a courier outage falls back to the stale cache instead of throwing")
    void courierFailureFallsBackToStaleCache() {
        TrackingResult stale = Fixtures.withStatus(TN, Courier.DHL, ShipmentStatus.IN_TRANSIT);
        when(cache.get(TN, Courier.DHL)).thenReturn(Optional.empty());
        when(dhlClient.track(TN)).thenThrow(courierApiException());
        when(cache.getStale(TN, Courier.DHL)).thenReturn(Optional.of(stale));

        assertThat(service.track(TN, Courier.DHL)).containsSame(stale);

        verify(cache).getStale(TN, Courier.DHL);
    }

    @Test
    @DisplayName("a courier outage with no stale entry rethrows the original exception")
    void courierFailureWithoutStaleCacheRethrows() {
        CourierApiException thrown = courierApiException();
        when(cache.get(TN, Courier.DHL)).thenReturn(Optional.empty());
        when(dhlClient.track(TN)).thenThrow(thrown);
        when(cache.getStale(TN, Courier.DHL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.track(TN, Courier.DHL))
                .isSameAs(thrown);
    }

    @Test
    @DisplayName("a non-CourierApiException propagates without consulting the stale cache")
    void otherExceptionsPropagateUnchanged() {
        IllegalStateException boom = new IllegalStateException("boom");
        when(cache.get(TN, Courier.DHL)).thenReturn(Optional.empty());
        when(dhlClient.track(TN)).thenThrow(boom);

        assertThatThrownBy(() -> service.track(TN, Courier.DHL)).isSameAs(boom);

        verify(cache, never()).getStale(any(), any());
    }

    private static CourierApiException courierApiException() {
        return new CourierApiException(Courier.DHL, TN, 500, new RuntimeException("upstream"));
    }
}
