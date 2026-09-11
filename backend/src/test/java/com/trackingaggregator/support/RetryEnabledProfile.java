package com.trackingaggregator.support;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * The ONLY {@link QuarkusTestProfile} in the suite — every extra profile costs a full
 * Quarkus restart, so resist adding a second one.
 *
 * <p>Re-enables MicroProfile Fault Tolerance (switched off suite-wide in
 * {@code src/test/resources/application.yml}) with a zero retry delay and a circuit breaker
 * threshold high enough not to trip, so {@code @Retry} behaviour can be asserted in
 * milliseconds rather than seconds.
 */
public class RetryEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "MP_Fault_Tolerance_NonFallback_Enabled", "true",
                "com.trackingaggregator.client.dhl.DHLClient/track/Retry/delay", "0",
                "com.trackingaggregator.client.dhl.DHLClient/track/Retry/maxRetries", "2",
                "com.trackingaggregator.client.dhl.DHLClient/track/CircuitBreaker/requestVolumeThreshold", "1000");
    }
}
