package com.trackingaggregator.client.fedex;

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
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class FedExClientTest extends CourierClientContract {

    private static final String PATH = "/track/v1/trackingnumbers";

    @Inject
    FedExClient fedExClient;

    @Override
    protected CourierClient client() {
        return fedExClient;
    }

    @Override
    protected Courier courier() {
        return Courier.FEDEX;
    }

    @Override
    protected String trackingNumber() {
        return Fixtures.FEDEX_NUMBER;
    }

    @Override
    protected MappingBuilder request() {
        return post(urlPathEqualTo(PATH));
    }

    @Override
    protected String successBody() {
        return Json.fixture("fedex-delivered.json");
    }

    @Test
    @DisplayName("POSTs the tracking number in the request body with a bearer token")
    void postsTrackingNumberWithBearerToken() {
        stub(request().willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(successBody())));

        fedExClient.track(trackingNumber());

        CourierApiTestResource.wiremock().verify(postRequestedFor(urlPathEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .withRequestBody(containing(trackingNumber())));
    }

    @Test
    @DisplayName("a 401 invalidates the cached OAuth token exactly once")
    void unauthorizedInvalidatesTheToken() {
        stub(request().willReturn(aResponse().withStatus(401)));

        assertThatThrownBy(() -> fedExClient.track(trackingNumber()))
                .isInstanceOf(CourierApiException.class);

        Mockito.verify(oAuthTokenService, Mockito.times(1)).invalidate("fedex");
    }
}
