package com.trackingaggregator.client.fedex;

import com.trackingaggregator.client.fedex.FedExTrackingResponse.CompleteTrackResult;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.DeliveryWindow;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.EstimatedDeliveryTimeWindow;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.FedExOutput;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.LatestStatusDetail;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.ScanEvent;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.ScanLocation;
import com.trackingaggregator.client.fedex.FedExTrackingResponse.TrackResult;
import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** In the production package so the package-private StatusMapper field can be assigned. */
class FedExResponseMapperTest {

    private static final String TN = "123456789012";

    private FedExResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FedExResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static FedExTrackingResponse of(TrackResult trackResult) {
        return new FedExTrackingResponse(new FedExOutput(
                List.of(new CompleteTrackResult(trackResult == null ? null : List.of(trackResult)))));
    }

    private static TrackResult trackResult(String derivedCode, String etaEnds, List<ScanEvent> events) {
        return new TrackResult(
                derivedCode == null ? null : new LatestStatusDetail("DL", "Delivered", derivedCode),
                etaEnds == null ? null : new EstimatedDeliveryTimeWindow(new DeliveryWindow(etaEnds)),
                events);
    }

    private static ScanEvent scanEvent(String date, String city, String state, String derivedStatus) {
        ScanLocation location = (city == null && state == null)
                ? null
                : new ScanLocation(city, state, "US");
        return new ScanEvent(date, "Arrived at FedEx location", derivedStatus, location);
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    void nullOutputIsEmpty() {
        assertThat(mapper.map(TN, new FedExTrackingResponse(null))).isEmpty();
    }

    @Test
    void nullOrEmptyCompleteTrackResultsIsEmpty() {
        assertThat(mapper.map(TN, new FedExTrackingResponse(new FedExOutput(null)))).isEmpty();
        assertThat(mapper.map(TN, new FedExTrackingResponse(new FedExOutput(List.of())))).isEmpty();
    }

    @Test
    void nullOrEmptyTrackResultsIsEmpty() {
        assertThat(mapper.map(TN, of(null))).isEmpty();
        assertThat(mapper.map(TN, new FedExTrackingResponse(new FedExOutput(
                List.of(new CompleteTrackResult(List.of())))))).isEmpty();
    }

    @Test
    void mapsAFullyPopulatedTrackResult() {
        FedExTrackingResponse response = of(trackResult("DL", "2026-09-15T12:00:00-05:00",
                List.of(scanEvent("2026-09-10T14:30:00-05:00", "MEMPHIS", "TN", "IT"))));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.FEDEX);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEstimatedDelivery()).isEqualTo(LocalDate.of(2026, 9, 15));

        var event = result.getEvents().getFirst();
        assertThat(event.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-09-10T14:30:00-05:00"));
        assertThat(event.getLocation()).isEqualTo("MEMPHIS, TN");
        assertThat(event.getDescription()).isEqualTo("Arrived at FedEx location");
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    void missingDeliveryWindowLeavesEstimatedDeliveryNull() {
        assertThat(mapper.map(TN, of(trackResult("IT", null, List.of())))
                .orElseThrow().getEstimatedDelivery()).isNull();

        FedExTrackingResponse nullWindow = of(new TrackResult(
                new LatestStatusDetail("IT", "In transit", "IT"),
                new EstimatedDeliveryTimeWindow(null),
                List.of()));
        assertThat(mapper.map(TN, nullWindow).orElseThrow().getEstimatedDelivery()).isNull();
    }

    @Test
    void nullLatestStatusDetailBecomesUnknown() {
        assertThat(mapper.map(TN, of(trackResult(null, null, List.of())))
                .orElseThrow().getStatus()).isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void nullScanEventsBecomeAnEmptyList() {
        assertThat(mapper.map(TN, of(trackResult("IT", null, null)))
                .orElseThrow().getEvents()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("a state with no city produces no leading comma")
    void stateWithoutCityHasNoLeadingComma() {
        FedExTrackingResponse response = of(trackResult("IT", null,
                List.of(scanEvent("2026-09-10T14:30:00-05:00", null, "TN", "IT"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation())
                .isEqualTo("TN");
    }

    @Test
    void missingScanLocationProducesNoLocation() {
        FedExTrackingResponse response = of(trackResult("IT", null,
                List.of(scanEvent("2026-09-10T14:30:00-05:00", null, null, "IT"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation()).isNull();
    }

    @Test
    void unparseableScanDateBecomesNull() {
        FedExTrackingResponse response = of(trackResult("IT", null,
                List.of(scanEvent("2026-09-10", "MEMPHIS", "TN", "IT"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }
}
