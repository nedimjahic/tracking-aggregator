package com.trackingaggregator.integration;

import com.github.tomakehurst.wiremock.http.Fault;
import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.dhl.DHLClient;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Fixtures;
import com.trackingaggregator.support.Json;
import com.trackingaggregator.support.RetryEnabledProfile;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suite's one {@link RetryEnabledProfile} test: fault tolerance is disabled everywhere
 * else (see {@code src/test/resources/application.yml} and plan risk R3), so {@code @Retry}
 * behaviour on {@link DHLClient} can only be observed here, in isolation, with the profile's
 * zero-delay override so the assertions run in milliseconds instead of seconds.
 *
 * <p>{@code @TestProfile} forces a second, separate Quarkus boot for this one class - accepted
 * cost per plan R9 ("one boot, or one boot per class").
 */
@QuarkusTest
@TestProfile(RetryEnabledProfile.class)
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class DHLClientRetryTest {

    private static final String PATH = "/track/shipments";

    @Inject
    DHLClient dhlClient;

    @BeforeEach
    void resetWireMock() {
        CourierApiTestResource.wiremock().resetAll();
    }

    @Test
    @DisplayName("a persistent 500 is retried exactly maxRetries+1 times before giving up")
    void persistentFailureRetriesThenThrows() {
        CourierApiTestResource.wiremock().stubFor(
                get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> dhlClient.track(Fixtures.DHL_NUMBER))
                .isInstanceOf(CourierApiException.class);

        CourierApiTestResource.wiremock().verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("a persistent transport failure is retried exactly maxRetries+1 times before giving up")
    void persistentTransportFailureRetriesThenThrows() {
        CourierApiTestResource.wiremock().stubFor(
                get(urlPathEqualTo(PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> dhlClient.track(Fixtures.DHL_NUMBER))
                .isInstanceOf(CourierApiException.class);

        CourierApiTestResource.wiremock().verify(3, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("a 404 is a single attempt - an empty result is not a retryable failure")
    void notFoundIsNotRetried() {
        CourierApiTestResource.wiremock().stubFor(
                get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThat(dhlClient.track(Fixtures.DHL_NUMBER)).isEmpty();

        CourierApiTestResource.wiremock().verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    @DisplayName("a transient 500 followed by a 200 recovers on the second attempt")
    void transientFailureRecoversOnRetry() {
        String scenarioName = "dhl-transient-failure";
        CourierApiTestResource.wiremock().stubFor(
                get(urlPathEqualTo(PATH)).inScenario(scenarioName)
                        .whenScenarioStateIs(STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("recovered"));
        CourierApiTestResource.wiremock().stubFor(
                get(urlPathEqualTo(PATH)).inScenario(scenarioName)
                        .whenScenarioStateIs("recovered")
                        .willReturn(aResponse().withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(Json.fixture("dhl-delivered.json"))));

        var result = dhlClient.track(Fixtures.DHL_NUMBER);

        assertThat(result).isPresent();
        CourierApiTestResource.wiremock().verify(2, getRequestedFor(urlPathEqualTo(PATH)));
    }
}
