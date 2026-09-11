package com.trackingaggregator.client.gls;

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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class GLSClientTest extends CourierClientContract {

    private static final String PATH = "/public/v1/tracking/references/" + Fixtures.GLS_NUMBER;

    @Inject
    GLSClient glsClient;

    @Override
    protected CourierClient client() {
        return glsClient;
    }

    @Override
    protected Courier courier() {
        return Courier.GLS;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.GLS_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return get(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("gls-in-transit.json");
    }

    @Test
    @DisplayName("sends the X-API-Key header")
    void sendsApiKeyHeader() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        glsClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("X-API-Key", equalTo("test-gls-key")));
    }

    @Test
    void doesNotUseOAuth() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        glsClient.track(trackingNumber());

        verifyNoInteractions(oAuthTokenService);
    }

    @Test
    @DisplayName("shipment status comes from free text, so INTRANSIT maps via substring match")
    void mapsProgressBarTextToStatus() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        var result = glsClient.track(trackingNumber()).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(result.getEvents()).hasSize(1);
        assertThat(result.getEvents().getFirst().getLocation()).isEqualTo("Neuenstein");
    }
}
