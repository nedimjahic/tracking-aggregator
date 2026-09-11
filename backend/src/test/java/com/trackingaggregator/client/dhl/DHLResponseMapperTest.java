package com.trackingaggregator.client.dhl;

import com.trackingaggregator.client.dhl.DHLTrackingResponse.DHLAddress;
import com.trackingaggregator.client.dhl.DHLTrackingResponse.DHLEvent;
import com.trackingaggregator.client.dhl.DHLTrackingResponse.DHLLocation;
import com.trackingaggregator.client.dhl.DHLTrackingResponse.DHLShipment;
import com.trackingaggregator.client.dhl.DHLTrackingResponse.DHLStatus;
import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lives in the production package so the package-private {@code @Inject StatusMapper}
 * field can be assigned directly instead of booting CDI. Making that field private
 * would break this test.
 */
class DHLResponseMapperTest {

    private static final String TN = "1234567890";

    private DHLResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DHLResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static DHLTrackingResponse response(DHLShipment shipment) {
        return new DHLTrackingResponse(shipment == null ? null : List.of(shipment));
    }

    private static DHLShipment shipment(String statusCode, String eta, List<DHLEvent> events) {
        return new DHLShipment(new DHLStatus(statusCode, null, null), eta, events);
    }

    private static DHLEvent event(String timestamp, String description, String statusCode, String locality) {
        DHLLocation location = locality == null ? null : new DHLLocation(new DHLAddress(locality));
        return new DHLEvent(timestamp, description, statusCode, location);
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    void nullShipmentsIsEmpty() {
        assertThat(mapper.map(TN, new DHLTrackingResponse(null))).isEmpty();
    }

    @Test
    void emptyShipmentsIsEmpty() {
        assertThat(mapper.map(TN, new DHLTrackingResponse(List.of()))).isEmpty();
    }

    @Test
    void mapsAFullyPopulatedShipment() {
        DHLTrackingResponse response = response(shipment("delivered", "2026-09-15", List.of(
                event("2026-09-10T14:30:00+02:00", "Processed at facility", "transit", "Frankfurt"),
                event("2026-09-15T09:05:00+02:00", "Delivered", "delivered", "Berlin"))));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.DHL);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEstimatedDelivery()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(result.getEvents()).hasSize(2);

        var first = result.getEvents().getFirst();
        assertThat(first.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-09-10T14:30:00+02:00"));
        assertThat(first.getLocation()).isEqualTo("Frankfurt");
        assertThat(first.getDescription()).isEqualTo("Processed at facility");
        assertThat(first.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void nullStatusBlockYieldsUnknownRatherThanNpe() {
        DHLTrackingResponse response = response(new DHLShipment(null, null, List.of()));

        assertThat(mapper.map(TN, response).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void parsesAnIsoTimestampEtaDownToItsDate() {
        DHLTrackingResponse response = response(shipment("transit", "2026-09-15T10:00:00+02:00", List.of()));

        assertThat(mapper.map(TN, response).orElseThrow().getEstimatedDelivery())
                .isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("an ETA without a UTC offset falls back to LocalDateTime, assumed UTC")
    void etaWithoutOffsetIsParsedAsUtc() {
        DHLTrackingResponse response = response(shipment("transit", "2026-09-15T10:00:00", List.of()));

        assertThat(mapper.map(TN, response).orElseThrow().getEstimatedDelivery())
                .isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    @DisplayName("an event timestamp without a UTC offset falls back to LocalDateTime, assumed UTC")
    void eventTimestampWithoutOffsetIsParsedAsUtc() {
        DHLTrackingResponse response = response(shipment("transit", null,
                List.of(event("2026-09-15T10:00:00", "Scan", "transit", "Frankfurt"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp())
                .isEqualTo(OffsetDateTime.parse("2026-09-15T10:00:00Z"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"15/09/2026", "not-a-date"})
    void unparseableEtaBecomesNullWithoutThrowing(String eta) {
        DHLTrackingResponse response = response(shipment("transit", eta, List.of()));

        assertThat(mapper.map(TN, response).orElseThrow().getEstimatedDelivery()).isNull();
    }

    @Test
    void unparseableEventTimestampBecomesNull() {
        DHLTrackingResponse response = response(shipment("transit", null,
                List.of(event("10.09.2026", "Scan", "transit", "Frankfurt"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }

    @Test
    void nullEventTimestampBecomesNull() {
        DHLTrackingResponse response = response(shipment("transit", null,
                List.of(event(null, "Scan", "transit", "Frankfurt"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }

    @Test
    void missingLocationBecomesNull() {
        DHLTrackingResponse withoutLocation = response(shipment("transit", null,
                List.of(event("2026-09-10T14:30:00+02:00", "Scan", "transit", null))));
        assertThat(mapper.map(TN, withoutLocation).orElseThrow()
                .getEvents().getFirst().getLocation()).isNull();

        DHLEvent withoutAddress =
                new DHLEvent("2026-09-10T14:30:00+02:00", "Scan", "transit", new DHLLocation(null));
        DHLTrackingResponse response = response(shipment("transit", null, List.of(withoutAddress)));
        assertThat(mapper.map(TN, response).orElseThrow()
                .getEvents().getFirst().getLocation()).isNull();
    }

    @Test
    @DisplayName("null events become an empty list, never a null list")
    void nullEventsBecomeAnEmptyList() {
        Optional<TrackingResult> result = mapper.map(TN, response(shipment("transit", null, null)));

        // The generated model declares events @NotNull, so a null here would fail
        // bean validation on the way out of the resource.
        assertThat(result.orElseThrow().getEvents()).isNotNull().isEmpty();
    }

    @Test
    void unknownEventStatusCodeBecomesUnknown() {
        DHLTrackingResponse response = response(shipment("transit", null,
                List.of(event("2026-09-10T14:30:00+02:00", "Scan", "no-such-code", "Frankfurt"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }
}
