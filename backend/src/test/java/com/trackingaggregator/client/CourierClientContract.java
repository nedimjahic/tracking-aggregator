package com.trackingaggregator.client;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.service.OAuthTokenService;
import com.trackingaggregator.support.CourierApiTestResource;
import io.quarkus.test.InjectMock;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * The behaviour every courier client is expected to share, run once per courier.
 *
 * <p>Uses WireMock rather than a mocked {@code @RestClient} on purpose: the branch under
 * test is {@code catch (WebApplicationException e) { if (status == 404) ... }}, and
 * hand-throwing a WebApplicationException would only assert our own assumption about
 * what the REST client raises. Going through real HTTP also covers header/query wiring
 * and JSON deserialisation into the DTO records.
 *
 * <p>Fault tolerance is disabled suite-wide in the test config, so each call here is a
 * single attempt. {@code DHLClientRetryTest} re-enables it in isolation.
 */
public abstract class CourierClientContract {

    /** Replaced globally so UPS/FedEx/USPS never make a real token call. */
    @InjectMock
    protected OAuthTokenService oAuthTokenService;

    @BeforeEach
    void resetStubsAndToken() {
        // One WireMock serves all six couriers plus three OAuth endpoints, so stubs and
        // request counters would otherwise bleed between classes.
        CourierApiTestResource.wiremock().resetAll();
        Mockito.when(oAuthTokenService.getToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("test-token");
    }

    // ---- per-courier hooks -------------------------------------------------

    protected abstract CourierClient client();

    protected abstract Courier courier();

    protected abstract String trackingNumber();

    /** A WireMock mapping matching this courier's tracking endpoint. */
    protected abstract MappingBuilder request();

    /** A valid response body for this courier's DTO. */
    protected abstract String successBody();

    /** Does an empty JSON object map to an absent shipment for this courier? */
    protected boolean emptyBodyMeansNotFound() {
        return true;
    }

    // ---- shared helpers ----------------------------------------------------

    protected static void stub(MappingBuilder mapping) {
        CourierApiTestResource.wiremock().stubFor(mapping);
    }

    private void stubStatus(int status) {
        stub(request().willReturn(aResponse().withStatus(status)));
    }

    private void stubBody(String body) {
        stub(request().willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    // ---- shared contract ---------------------------------------------------

    @Test
    void reportsItsCourier() {
        assertThat(client().getCourier()).isEqualTo(courier());
    }

    @Test
    void isEnabledInTheTestProfile() {
        assertThat(client().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a valid 200 maps to a result")
    void successfulResponseIsMapped() {
        stubBody(successBody());

        var result = client().track(trackingNumber());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().getCourier()).isEqualTo(courier());
        assertThat(result.orElseThrow().getTrackingNumber()).isEqualTo(trackingNumber());
    }

    @Test
    @DisplayName("an empty JSON object means no shipment")
    void emptyBodyIsNotFound() {
        stubBody("{}");

        if (emptyBodyMeansNotFound()) {
            assertThat(client().track(trackingNumber())).isEmpty();
        } else {
            assertThat(client().track(trackingNumber())).isPresent();
        }
    }

    @Test
    @DisplayName("404 is an absent shipment, not an error")
    void notFoundReturnsEmpty() {
        stubStatus(404);

        assertThat(client().track(trackingNumber())).isEmpty();
    }

    @ParameterizedTest(name = "HTTP {0} becomes CourierApiException")
    @ValueSource(ints = {500, 502, 503, 429, 401, 403})
    void errorStatusesBecomeCourierApiException(int status) {
        stubStatus(status);

        assertThatThrownBy(() -> client().track(trackingNumber()))
                .isInstanceOf(CourierApiException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(CourierApiException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                    assertThat(e.getCourier()).isEqualTo(courier());
                    assertThat(e.getTrackingNumber()).isEqualTo(trackingNumber());
                    assertThat(e.getCause()).isInstanceOf(jakarta.ws.rs.WebApplicationException.class);
                });
    }

    @Test
    @DisplayName("a malformed 200 body becomes a CourierApiException reporting httpStatus=502")
    void malformedBodyBecomesCourierApiExceptionWithBadGatewayStatus() {
        // The Quarkus REST Client wraps a Jackson deserialization failure into a
        // WebApplicationException whose embedded Response carries the ORIGINAL 200 status.
        // Reporting httpStatus == 200 on the resulting CourierApiException would be
        // misleading (the failure is a deserialization error, not a courier-side outage),
        // so it is normalized to 502 instead. Either way, CourierApiExceptionMapper still
        // answers the client with 503 "COURIER_UNAVAILABLE".
        stubBody("{ this is not json");

        assertThatThrownBy(() -> client().track(trackingNumber()))
                .isInstanceOf(CourierApiException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(CourierApiException.class))
                .satisfies(e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(502);
                    assertThat(e.getCourier()).isEqualTo(courier());
                    assertThat(e.getTrackingNumber()).isEqualTo(trackingNumber());
                });
    }

    @Test
    @DisplayName("a transport failure is wrapped as a CourierApiException")
    void transportFailureIsWrapped() {
        // A connection reset, DNS failure or read timeout surfaces from the REST client as
        // a ProcessingException. It is now wrapped into a CourierApiException so it reaches
        // CourierApiExceptionMapper (503) and TrackingService's stale-cache fallback, the
        // same as an HTTP-level courier failure.
        stub(request().willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client().track(trackingNumber()))
                .isInstanceOf(CourierApiException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(CourierApiException.class))
                .satisfies(e -> {
                    assertThat(e.getCourier()).isEqualTo(courier());
                    assertThat(e.getTrackingNumber()).isEqualTo(trackingNumber());
                    assertThat(e.getCause()).isInstanceOf(ProcessingException.class);
                });
    }
}
