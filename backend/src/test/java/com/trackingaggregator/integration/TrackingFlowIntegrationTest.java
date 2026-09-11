package com.trackingaggregator.integration;

import com.github.tomakehurst.wiremock.http.Fault;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Db;
import com.trackingaggregator.support.Fixtures;
import com.trackingaggregator.support.Json;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * The single highest-value test in the plan: zero mocks anywhere in the stack.
 * Real HTTP routing -> real {@code TrackingResourceImpl} -> real {@code TrackingService} ->
 * real {@code CourierDetectorService} -> a real (WireMock-stubbed) courier client ->
 * a real Postgres-backed cache and analytics table.
 */
@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class TrackingFlowIntegrationTest {

    private static final String DHL_PATH = "/track/shipments";
    private static final String GLS_PATH = "/public/v1/tracking/references/" + Fixtures.GLS_NUMBER;

    @BeforeEach
    void reset() {
        CourierApiTestResource.wiremock().resetAll();
        Db.clean();
    }

    @Test
    @DisplayName("a 10-digit number is detected as DHL, tracked, cached and recorded")
    void dhlHappyPathIsCachedAndRecorded() {
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Json.fixture("dhl-delivered.json"))));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then()
                .statusCode(200)
                .body("trackingNumber", is(Fixtures.DHL_NUMBER))
                .body("courier", is("DHL"))
                .body("status", is("DELIVERED"));

        org.assertj.core.api.Assertions.assertThat(Db.cacheCount()).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(Db.queryCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a second immediate lookup is served from cache, not the courier")
    void secondLookupIsServedFromCache() {
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Json.fixture("dhl-delivered.json"))));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then().statusCode(200)
                .body("status", is("DELIVERED"));

        // Not a literal string comparison against the first response: the cache round-trip
        // re-serializes OffsetDateTime through Jackson, which normalizes the zone offset
        // (e.g. "+02:00" becomes "Z") while representing the exact same instant. Comparing
        // the semantically-relevant fields avoids coupling the test to that normalization.
        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then().statusCode(200)
                .body("trackingNumber", is(Fixtures.DHL_NUMBER))
                .body("courier", is("DHL"))
                .body("status", is("DELIVERED"))
                .body("estimatedDelivery", is("2026-09-15"))
                .body("events.size()", is(2));

        CourierApiTestResource.wiremock().verify(1, getRequestedFor(urlPathEqualTo(DHL_PATH)));
        org.assertj.core.api.Assertions.assertThat(Db.queryCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a stale cache row is served when the courier is down (end-to-end 503 avoidance)")
    void staleCacheIsServedOnCourierOutage() {
        String json = """
                {"trackingNumber":"%s","courier":"DHL","status":"IN_TRANSIT","events":[]}
                """.formatted(Fixtures.DHL_NUMBER);
        Db.seedCache(Fixtures.DHL_NUMBER, "DHL", json, "IN_TRANSIT", LocalDateTime.now().minusHours(2));
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withStatus(500)));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then()
                .statusCode(200)
                .body("status", is("IN_TRANSIT"));
    }

    @Test
    @DisplayName("a stale cache row is served when the courier connection is reset (transport failure)")
    void staleCacheIsServedOnTransportFailure() {
        String json = """
                {"trackingNumber":"%s","courier":"DHL","status":"IN_TRANSIT","events":[]}
                """.formatted(Fixtures.DHL_NUMBER);
        Db.seedCache(Fixtures.DHL_NUMBER, "DHL", json, "IN_TRANSIT", LocalDateTime.now().minusHours(2));
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then()
                .statusCode(200)
                .body("status", is("IN_TRANSIT"));
    }

    @Test
    @DisplayName("no stale cache and the courier is down -> 503")
    void courierOutageWithNoCacheIs503() {
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withStatus(500)));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then()
                .statusCode(503)
                .body("error", is("COURIER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("an undetectable tracking number never reaches WireMock")
    void undetectableTrackingNumberMakesNoCourierCall() {
        RestAssured.given()
                .when().get("/api/track/ABC")
                .then()
                .statusCode(400)
                .body("error", is("COURIER_NOT_DETECTED"));

        CourierApiTestResource.wiremock().verify(0, getRequestedFor(urlPathEqualTo(DHL_PATH)));
        CourierApiTestResource.wiremock().verify(0, getRequestedFor(urlPathEqualTo(GLS_PATH)));
    }

    @Test
    @DisplayName("an 11-digit number is detected as GLS end-to-end")
    void glsHappyPathIsDetectedAndCached() {
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(GLS_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(Json.fixture("gls-in-transit.json"))));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.GLS_NUMBER)
                .then()
                .statusCode(200)
                .body("courier", is("GLS"))
                .body("status", is("IN_TRANSIT"));

        org.assertj.core.api.Assertions.assertThat(Db.findCache(Fixtures.GLS_NUMBER, "GLS")).isNotNull();
    }

    @Test
    @DisplayName("a courier 404 is a clean 404 with nothing written to either table")
    void courier404WritesNothing() {
        CourierApiTestResource.wiremock().stubFor(get(urlPathEqualTo(DHL_PATH))
                .willReturn(aResponse().withStatus(404)));

        RestAssured.given()
                .when().get("/api/track/" + Fixtures.DHL_NUMBER)
                .then()
                .statusCode(404)
                .body("error", is("TRACKING_NOT_FOUND"));

        org.assertj.core.api.Assertions.assertThat(Db.cacheCount()).isZero();
        org.assertj.core.api.Assertions.assertThat(Db.queryCount()).isZero();
    }
}
