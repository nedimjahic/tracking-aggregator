package com.trackingaggregator.resource;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.service.TrackingService;
import com.trackingaggregator.support.CourierApiTestResource;
import com.trackingaggregator.support.Fixtures;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.TestResourceScope;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises the real JAX-RS pipeline (routing, validation, {@link CourierApiExceptionMapper}
 * provider discovery) with {@code TrackingService} mocked out. {@code TrackingService} has a
 * non-default constructor; Mockito instantiates it via Objenesis for {@code @InjectMock}, which
 * requires the class (and the methods stubbed below) to stay non-final - see plan risk R12.
 */
@QuarkusTest
@WithTestResource(value = CourierApiTestResource.class, scope = TestResourceScope.GLOBAL)
class TrackingResourceTest {

    private static final String TN = Fixtures.DHL_NUMBER;

    @InjectMock
    TrackingService trackingService;

    @BeforeEach
    void resetMock() {
        Mockito.reset(trackingService);
    }

    @Test
    @DisplayName("200: a detected courier and a found shipment are returned as-is")
    void returnsShipment() {
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL))
                .thenReturn(Optional.of(Fixtures.delivered(TN, Courier.DHL)));

        RestAssured.given()
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(200)
                .body("trackingNumber", is(TN))
                .body("courier", is("DHL"))
                .body("status", is("DELIVERED"))
                .body("events.size()", is(2))
                // Exact string, not just "not null" - catches LocalDate ever serializing
                // as [2026,9,15] if the JavaTimeModule falls out of the ObjectMapper config.
                .body("estimatedDelivery", is("2026-09-15"))
                .body("events[0].timestamp", is("2026-09-10T14:30:00+02:00"));
    }

    @Test
    @DisplayName("400: courier not detected")
    void courierNotDetected() {
        when(trackingService.detectCourier("ABC")).thenReturn(Optional.empty());

        RestAssured.given()
                .when().get("/api/track/ABC")
                .then()
                .statusCode(400)
                .body("error", is("COURIER_NOT_DETECTED"))
                .body("trackingNumber", is("ABC"))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()));

        verify(trackingService, Mockito.never()).track(any(), any());
    }

    @Test
    @DisplayName("404: courier detected but no shipment found")
    void shipmentNotFound() {
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL)).thenReturn(Optional.empty());

        RestAssured.given()
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(404)
                .body("error", is("TRACKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("503: a CourierApiException from the service is mapped by the @Provider")
    void courierUnavailable() {
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL))
                .thenThrow(new CourierApiException(Courier.DHL, TN, 500, new RuntimeException("upstream")));

        RestAssured.given()
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(503)
                .body("error", is("COURIER_UNAVAILABLE"))
                .body("message", org.hamcrest.Matchers.containsString("DHL"));
    }

    @Test
    @DisplayName("a disabled courier is indistinguishable from a missing shipment (404)")
    void disabledCourierLooksLikeNotFound() {
        // TrackingService.track() returns Optional.empty() both when the client is
        // unregistered/disabled and when the courier genuinely has no record of the
        // shipment. The resource - and thus the API consumer - cannot tell them apart.
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL)).thenReturn(Optional.empty());

        RestAssured.given()
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(404)
                .body("error", is("TRACKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("a path segment over the 50-char @Size limit is rejected with 400")
    void trackingNumberTooLongIsRejected() {
        String tooLong = "1".repeat(51);

        RestAssured.given()
                .when().get("/api/track/" + tooLong)
                .then()
                .statusCode(400);

        // API contract gap: openapi.yml documents a TrackingError body for 400, but a
        // @Size constraint violation is reported by Quarkus's own violation report, not
        // by TrackingResourceImpl / TrackingError. Confirmed here rather than assumed.
        verifyNoInteractions(trackingService);
    }

    @Test
    @DisplayName("an empty tracking-number path segment 404s at routing, before the service runs")
    void emptyPathSegmentDoesNotMatchRoute() {
        RestAssured.given()
                .when().get("/api/track/")
                .then()
                .statusCode(404);

        verifyNoInteractions(trackingService);
    }

    @Test
    @DisplayName("an Accept header the resource cannot satisfy yields 406")
    void unacceptableMediaTypeIsRejected() {
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL))
                .thenReturn(Optional.of(Fixtures.delivered(TN, Courier.DHL)));

        RestAssured.given()
                .accept(ContentType.TEXT)
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(406);
    }

    @Test
    @DisplayName("500: an unexpected RuntimeException from the service is mapped without leaking detail")
    void unexpectedExceptionDoesNotLeakInternalDetail() {
        when(trackingService.detectCourier(TN)).thenReturn(Optional.of(Courier.DHL));
        when(trackingService.track(TN, Courier.DHL))
                .thenThrow(new RuntimeException("db password is hunter2"));

        String body = RestAssured.given()
                .when().get("/api/track/" + TN)
                .then()
                .statusCode(500)
                .body("error", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()))
                .body("message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString()))
                .extract().asString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("hunter2");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("RuntimeException");
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("at com.trackingaggregator");
    }
}
