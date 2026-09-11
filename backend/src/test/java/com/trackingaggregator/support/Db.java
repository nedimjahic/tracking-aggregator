package com.trackingaggregator.support;

import com.trackingaggregator.entity.TrackingCache;
import com.trackingaggregator.entity.TrackingQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;

import java.time.LocalDateTime;

/**
 * Database helpers for {@code @QuarkusTest} classes.
 *
 * <p>Everything here runs in its own transaction via {@link QuarkusTransaction}, deliberately
 * NOT via {@code @Transactional} or {@code @TestTransaction} on the test method:
 * <ul>
 *   <li>interceptor bindings on {@code @QuarkusTest} methods are not reliably applied;</li>
 *   <li>{@code TrackingService} persists its analytics row in {@code requiringNew()}, which
 *       commits independently of an enclosing {@code @TestTransaction} — so the row would
 *       survive that rollback and leak into the next test, and can deadlock on row locks.</li>
 * </ul>
 * Clean up explicitly with {@link #clean()} in {@code @BeforeEach} instead.
 *
 * <p>Every Panache static call below is wrapped in an explicit lambda ({@code () -> Foo.bar()}),
 * never passed as a bare method reference (e.g. {@code Foo::bar}). Panache rewrites static
 * methods like {@code count()}/{@code deleteAll()} via bytecode enhancement of the *call site*,
 * not the target method, and that enhancement does not apply to a method reference — at runtime
 * it throws {@code RuntimeException: This method is normally automatically overridden in
 * subclasses}. A lambda body containing the actual call site gets enhanced correctly.
 */
public final class Db {

    private Db() {
    }

    public static void clean() {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackingCache.deleteAll();
            TrackingQuery.deleteAll();
        });
    }

    /** Seeds a cache row with an explicit cachedAt so TTL behaviour can be exercised. */
    public static void seedCache(String trackingNumber, String courier, String responseJson,
                                 String shipmentStatus, LocalDateTime cachedAt) {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackingCache row = new TrackingCache();
            row.trackingNumber = trackingNumber;
            row.courier = courier;
            row.responseJson = responseJson;
            row.shipmentStatus = shipmentStatus;
            row.cachedAt = cachedAt;
            row.persist();
        });
    }

    public static long cacheCount() {
        return QuarkusTransaction.requiringNew().call(() -> TrackingCache.count());
    }

    public static long queryCount() {
        return QuarkusTransaction.requiringNew().call(() -> TrackingQuery.count());
    }

    public static TrackingQuery firstQuery() {
        return QuarkusTransaction.requiringNew()
                .call(() -> TrackingQuery.<TrackingQuery>findAll().firstResult());
    }

    public static TrackingCache findCache(String trackingNumber, String courier) {
        return QuarkusTransaction.requiringNew()
                .call(() -> TrackingCache.findByTrackingNumberAndCourier(trackingNumber, courier));
    }
}
