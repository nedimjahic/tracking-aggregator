package com.trackingaggregator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OAuthTokenService takes the token URL as a method parameter rather than reading it from
 * config, so it is fully testable against a local WireMock with no production changes and
 * no CDI. Only the package-private ObjectMapper field needs assigning, hence the package.
 */
class OAuthTokenServiceTest {

    private static final String CLIENT_ID = "test-id";
    private static final String CLIENT_SECRET = "test-secret";
    private static final String PATH = "/oauth/token";

    private WireMockServer wireMock;
    private OAuthTokenService service;
    private String tokenUrl;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        tokenUrl = wireMock.baseUrl() + PATH;

        service = new OAuthTokenService();
        service.objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null && wireMock.isRunning()) {
            wireMock.stop();
        }
    }

    private void stubToken(String body) {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body)));
    }

    private void stubStatus(int status) {
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)));
    }

    private String token(String courierKey) {
        return service.getToken(courierKey, tokenUrl, CLIENT_ID, CLIENT_SECRET);
    }

    private static String expectedBasicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString(
                (CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void requestsATokenWithBasicAuthAndClientCredentialsGrant() {
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":3600}");

        assertThat(token("ups")).isEqualTo("tok-1");

        wireMock.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo(expectedBasicAuth()))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=client_credentials")));
    }

    @Test
    @DisplayName("a still-valid token is served from cache")
    void cachesTheToken() {
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":3600}");

        assertThat(token("ups")).isEqualTo("tok-1");
        assertThat(token("ups")).isEqualTo("tok-1");

        wireMock.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    @DisplayName("a token expiring inside the 60s safety window is never cached")
    void shortLivedTokenIsRefetchedEveryTime() {
        // isExpired() compares against expiresAt minus 60 seconds, so a token with
        // expires_in below 60 is already "expired" the moment it arrives. Any courier
        // sandbox issuing short-lived tokens would be re-hit on every single request.
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":30}");

        token("ups");
        token("ups");

        wireMock.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void missingExpiresInDefaultsToOneHour() {
        stubToken("{\"access_token\":\"tok-1\"}");

        token("ups");
        token("ups");

        wireMock.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void invalidateForcesARefetch() {
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":3600}");

        token("ups");
        service.invalidate("ups");
        token("ups");

        wireMock.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void invalidateOnAnUnknownKeyIsHarmless() {
        service.invalidate("never-seen");
        // no exception
    }

    @Test
    @DisplayName("the cache is keyed per courier")
    void cacheIsPerCourierKey() {
        stubToken("{\"access_token\":\"tok-1\",\"expires_in\":3600}");

        token("ups");
        token("fedex");

        wireMock.verify(2, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void nonSuccessStatusReportsTheStatusAndCourier() {
        stubStatus(401);

        assertThatThrownBy(() -> token("ups"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("ups");
    }

    @Test
    void serverErrorReportsTheStatusAndCourier() {
        stubStatus(500);

        assertThatThrownBy(() -> token("fedex"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500")
                .hasMessageContaining("fedex");
    }

    @Test
    @DisplayName("a 200 with no access_token throws a clear error, not an NPE")
    void missingAccessTokenThrowsUsefulError() {
        stubToken("{\"token_type\":\"Bearer\",\"expires_in\":3600}");

        assertThatThrownBy(() -> token("ups"))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(NullPointerException.class)
                .hasMessageContaining("access_token")
                // must not leak the token response body, which may contain secrets
                .hasMessageNotContaining("token_type");
    }

    @Test
    void malformedJsonBodyIsWrappedInARuntimeException() {
        stubToken("not json");

        assertThatThrownBy(() -> token("ups"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to obtain OAuth token");
    }

    @Test
    @DisplayName("an unreachable token endpoint is wrapped, not leaked as IOException")
    void connectionFailureIsWrapped() {
        wireMock.stop();   // nothing is listening on that port any more

        assertThatThrownBy(() -> token("ups"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to obtain OAuth token")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("concurrent callers make exactly one token request")
    void concurrentCallersShareASingleFetch() throws Exception {
        // The double-checked locking in getToken is easy to break and impossible to
        // notice without this test: without it, ten simultaneous first-requests would
        // each hit the courier's token endpoint.
        wireMock.stubFor(post(urlEqualTo(PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"tok-1\",\"expires_in\":3600}")
                .withFixedDelay(300)));

        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        Set<String> tokens = Collections.synchronizedSet(new HashSet<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        tokens.add(token("ups"));
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failure.get()).isNull();
        assertThat(tokens).containsExactly("tok-1");
        wireMock.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }
}
