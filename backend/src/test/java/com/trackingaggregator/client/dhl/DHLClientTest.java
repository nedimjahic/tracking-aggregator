package com.trackingaggregator.client.dhl;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.client.CourierClientContract;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Fixtures;
import com.trackingaggregator.support.Json;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class DHLClientTest extends CourierClientContract {

    private static final String PATH = "/track/shipments";

    @Inject
    DHLClient dhlClient;

    @Override
    protected CourierClient client() {
        return dhlClient;
    }

    @Override
    protected Courier courier() {
        return Courier.DHL;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.DHL_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return get(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("dhl-delivered.json");
    }

    @Test
    @DisplayName("sends the API key header and the tracking number as a query parameter")
    void sendsApiKeyAndQueryParameter() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        dhlClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("DHL-API-Key", equalTo("test-dhl-key"))
                .withQueryParam("trackingNumber", equalTo(trackingNumber())));
    }

    @Test
    @DisplayName("DHL uses a static API key, so no OAuth token is requested")
    void doesNotUseOAuth() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        dhlClient.track(trackingNumber());

        verifyNoInteractions(oAuthTokenService);
    }

    @Test
    void mapsTheFixtureToADeliveredShipment() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        var result = dhlClient.track(trackingNumber()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(com.trackingaggregator.model.ShipmentStatus.DELIVERED);
        assertThat(result.getEvents()).hasSize(2);
        assertThat(result.getEvents().getFirst().getLocation()).isEqualTo("Frankfurt");
    }
}
