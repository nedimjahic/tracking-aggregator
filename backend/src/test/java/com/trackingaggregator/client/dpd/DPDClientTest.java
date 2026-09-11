package com.trackingaggregator.client.dpd;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.client.CourierClientContract;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Fixtures;
import com.trackingaggregator.support.Json;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class DPDClientTest extends CourierClientContract {

    private static final String PATH = "/rest/plc/en_US/shipment/" + Fixtures.DPD_NUMBER;

    @Inject
    DPDClient dpdClient;

    @Override
    protected CourierClient client() {
        return dpdClient;
    }

    @Override
    protected Courier courier() {
        return Courier.DPD;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.DPD_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return get(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("dpd-delivered.json");
    }

    @Test
    @DisplayName("sends no credentials at all despite courier.dpd.api-key being configured")
    void sendsNoAuthentication_knownGap() {
        // DPDRestClient declares no @HeaderParam, so the configured courier.dpd.api-key
        // is read into DPDClient and then never used. Either the integration is
        // incomplete, or the config key is dead and should be removed. Pinned so
        // whichever it is becomes a decision.
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        dpdClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", absent())
                .withHeader("X-API-Key", absent())
                .withHeader("DPD-API-Key", absent()));
        verifyNoInteractions(oAuthTokenService);
    }

    @Test
    @DisplayName("dd/MM/yyyy scan dates are parsed day-first")
    void parsesDayFirstScanDates() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        var result = dpdClient.track(trackingNumber()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEvents()).hasSize(1);
        // 25/12/2026 - a day-vs-month swap would land on an invalid month and null out.
        assertThat(result.getEvents().getFirst().getTimestamp())
                .isEqualTo(java.time.OffsetDateTime.parse("2026-12-25T14:30:00Z"));
        assertThat(result.getEvents().getFirst().getLocation()).isNull();
    }
}
