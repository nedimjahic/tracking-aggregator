package com.trackingaggregator.client.ups;

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
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class UPSClientTest extends CourierClientContract {

    private static final String PATH = "/api/track/v1/details/" + Fixtures.UPS_NUMBER;

    @Inject
    UPSClient upsClient;

    @Override
    protected CourierClient client() {
        return upsClient;
    }

    @Override
    protected Courier courier() {
        return Courier.UPS;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.UPS_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return get(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("ups-delivered.json");
    }

    @Test
    @DisplayName("sends the bearer token, a transaction source and a UUID transaction id")
    void sendsAuthAndTransactionHeaders() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        upsClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withHeader("transactionSrc", equalTo("tracking-aggregator"))
                .withHeader("transId", matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}"
                        + "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")));
    }

    @Test
    @DisplayName("a 401 invalidates the cached OAuth token exactly once")
    void unauthorizedInvalidatesTheToken() {
        // "exactly once" is only meaningful because fault tolerance is disabled in the
        // test config; with @Retry active a single 401 would invalidate three times and
        // trigger three token re-fetches against the courier.
        stub(request().willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> upsClient.track(trackingNumber()))
                .isInstanceOf(CourierApiException.class);

        Mockito.verify(oAuthTokenService, Mockito.times(1)).invalidate("ups");
    }

    @Test
    @DisplayName("a 500 does not invalidate the token")
    void serverErrorLeavesTheTokenAlone() {
        stub(request().willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> upsClient.track(trackingNumber()))
                .isInstanceOf(CourierApiException.class);

        Mockito.verify(oAuthTokenService, Mockito.never()).invalidate(Mockito.anyString());
    }
}
