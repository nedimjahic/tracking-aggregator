package com.trackingaggregator.client.usps;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.trackingaggregator.client.CourierApiException;
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
import org.mockito.Mockito;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class USPSClientTest extends CourierClientContract {

    private static final String PATH = "/tracking/v3/tracking/" + Fixtures.USPS_NUMBER;

    @Inject
    USPSClient uspsClient;

    @Override
    protected CourierClient client() {
        return uspsClient;
    }

    @Override
    protected Courier courier() {
        return Courier.USPS;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.USPS_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return get(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("usps-delivered.json");
    }

    @Test
    @DisplayName("sends the bearer token and the DETAIL expand parameter")
    void sendsAuthAndExpandParameter() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        uspsClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withQueryParam("expand", equalTo("DETAIL")));
    }

    @Test
    @DisplayName("a 401 invalidates the cached OAuth token exactly once")
    void unauthorizedInvalidatesTheToken() {
        stub(request().willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> uspsClient.track(trackingNumber()))
                .isInstanceOf(CourierApiException.class);

        Mockito.verify(oAuthTokenService, Mockito.times(1)).invalidate("usps");
    }
}
