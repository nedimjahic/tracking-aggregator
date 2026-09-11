package com.trackingaggregator.client.gls;

import com.trackingaggregator.client.gls.GLSTrackingResponse.GLSAddress;
import com.trackingaggregator.client.gls.GLSTrackingResponse.GLSHistoryEvent;
import com.trackingaggregator.client.gls.GLSTrackingResponse.GLSProgressBar;
import com.trackingaggregator.client.gls.GLSTrackingResponse.GLSTuStatus;
import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** In the production package so the package-private StatusMapper field can be assigned. */
class GLSResponseMapperTest {

    private static final String TN = "12345678901";

    private GLSResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GLSResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static GLSTrackingResponse of(String statusInfo, List<GLSHistoryEvent> history) {
        return new GLSTrackingResponse(List.of(new GLSTuStatus(
                history, statusInfo == null ? null : new GLSProgressBar(statusInfo))));
    }

    private static GLSHistoryEvent event(String date, String time, String city, String description) {
        return new GLSHistoryEvent(date, time, city == null ? null : new GLSAddress(city, "DE"), description);
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    void nullOrEmptyTuStatusIsEmpty() {
        assertThat(mapper.map(TN, new GLSTrackingResponse(null))).isEmpty();
        assertThat(mapper.map(TN, new GLSTrackingResponse(List.of()))).isEmpty();
    }

    @Test
    void mapsAFullyPopulatedStatus() {
        GLSTrackingResponse response = of("DELIVERED",
                List.of(event("2026-09-10", "14:30:00", "Neuenstein", "Parcel in transit")));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.GLS);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);

        var mapped = result.getEvents().getFirst();
        assertThat(mapped.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-09-10T14:30:00Z"));
        assertThat(mapped.getLocation()).isEqualTo("Neuenstein");
        assertThat(mapped.getDescription()).isEqualTo("Parcel in transit");
        // Event status is derived from the free-text description, not a status code.
        assertThat(mapped.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("GLS never populates estimatedDelivery")
    void estimatedDeliveryIsNeverSet() {
        // The mapper builds the result without calling .estimatedDelivery(), and the DTO
        // carries no such field. Pinned so the omission is a decision, not a regression.
        assertThat(mapper.map(TN, of("DELIVERED", List.of())).orElseThrow().getEstimatedDelivery())
                .isNull();
    }

    @Test
    void nullProgressBarBecomesUnknown() {
        assertThat(mapper.map(TN, of(null, List.of())).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void nullHistoryBecomesAnEmptyList() {
        assertThat(mapper.map(TN, of("DELIVERED", null)).orElseThrow().getEvents())
                .isNotNull().isEmpty();
    }

    @Test
    @DisplayName("a missing time defaults to midnight UTC")
    void missingTimeDefaultsToMidnight() {
        GLSTrackingResponse response = of("IN TRANSIT",
                List.of(event("2026-09-10", null, "Neuenstein", "Parcel in transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp())
                .isEqualTo(OffsetDateTime.parse("2026-09-10T00:00:00Z"));
    }

    @Test
    @DisplayName("GLS dates are ISO - a German-formatted date does not parse")
    void germanFormattedDateIsRejected() {
        GLSTrackingResponse response = of("IN TRANSIT",
                List.of(event("10.09.2026", "14:30:00", "Neuenstein", "Parcel in transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }

    @Test
    void nullAddressProducesNoLocation() {
        GLSTrackingResponse response = of("IN TRANSIT",
                List.of(event("2026-09-10", "14:30:00", null, "Parcel in transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation()).isNull();
    }

    @Test
    void nullDescriptionProducesNoDescriptionAndUnknownStatus() {
        GLSTrackingResponse response = of("IN TRANSIT",
                List.of(event("2026-09-10", "14:30:00", "Neuenstein", null)));

        var mapped = mapper.map(TN, response).orElseThrow().getEvents().getFirst();
        assertThat(mapped.getDescription()).isNull();
        assertThat(mapped.getStatus()).isEqualTo(ShipmentStatus.UNKNOWN);
    }
}
