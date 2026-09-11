package com.trackingaggregator.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts a real Postgres (the cache UPSERT uses Postgres-only ON CONFLICT) and a single
 * WireMock server standing in for all six courier APIs plus the three OAuth token endpoints.
 *
 * <p>Registered GLOBAL on every {@code @QuarkusTest} so the container, the WireMock server
 * and the Quarkus application itself are all started once for the whole suite.
 *
 * <p>The config map returned from {@link #start()} is applied as the highest-ordinal config
 * source, which is the only reliable way to beat the hardcoded localhost datasource url in
 * {@code src/main/resources/application.yml}.
 */
public class CourierApiTestResource implements QuarkusTestResourceLifecycleManager {

    private static PostgreSQLContainer<?> POSTGRES;
    private static WireMockServer WIREMOCK;

    public static WireMockServer wiremock() {
        return WIREMOCK;
    }

    public static String baseUrl() {
        return WIREMOCK.baseUrl();
    }

    @Override
    public Map<String, String> start() {
        POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("tracking_aggregator_test")
                .withUsername("tracking")
                .withPassword("tracking")
                .withReuse(true);
        POSTGRES.start();

        WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        WIREMOCK.start();
        String url = WIREMOCK.baseUrl();

        Map<String, String> cfg = new HashMap<>();
        cfg.put("quarkus.datasource.jdbc.url", POSTGRES.getJdbcUrl());
        cfg.put("quarkus.datasource.username", POSTGRES.getUsername());
        cfg.put("quarkus.datasource.password", POSTGRES.getPassword());
        for (String key : List.of("dhl", "ups", "fedex", "usps", "gls", "dpd")) {
            cfg.put("quarkus.rest-client." + key + "-api.url", url);
        }
        // Point the OAuth token urls at WireMock too, so a test that forgets to mock
        // OAuthTokenService fails with a local connection error instead of reaching
        // onlinetools.ups.com for real.
        cfg.put("courier.ups.token-url", url + "/oauth/ups/token");
        cfg.put("courier.fedex.token-url", url + "/oauth/fedex/token");
        cfg.put("courier.usps.token-url", url + "/oauth/usps/token");
        return cfg;
    }

    @Override
    public void stop() {
        if (WIREMOCK != null) {
            WIREMOCK.stop();
        }
        if (POSTGRES != null) {
            POSTGRES.stop();
        }
    }
}
