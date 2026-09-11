package com.trackingaggregator.client.usps;

import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.client.usps.USPSTrackingResponse.USPSEvent;
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
class USPSResponseMapperTest {

    private static final String TN = "12345678901234567890";

    private USPSResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new USPSResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static USPSEvent event(String timestamp, String city, String state, String type) {
        return new USPSEvent(timestamp, city, state, "Arrived at facility", type);
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    @DisplayName("an all-null body is treated as no shipment, like the other couriers")
    void emptyBodyIsNotFound() {
        USPSTrackingResponse empty = new USPSTrackingResponse(null, null, null, null, null);

        assertThat(mapper.map(TN, empty)).isEmpty();
    }

    @Test
    void mapsAFullyPopulatedResponse() {
        USPSTrackingResponse response = new USPSTrackingResponse(
                TN, "Delivered", "Your item was delivered", "2026-09-15",
                List.of(event("2026-09-10T14:30:00-04:00", "New York", "NY", "In Transit")));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.USPS);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEstimatedDelivery()).isEqualTo(LocalDate.of(2026, 9, 15));

        var mapped = result.getEvents().getFirst();
        assertThat(mapped.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-09-10T14:30:00-04:00"));
        assertThat(mapped.getLocation()).isEqualTo("New York, NY");
        assertThat(mapped.getDescription()).isEqualTo("Arrived at facility");
        assertThat(mapped.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("a US-formatted expected delivery date is dropped")
    void nonIsoExpectedDeliveryDateIsDropped() {
        USPSTrackingResponse response =
                new USPSTrackingResponse(TN, "Delivered", null, "09/15/2026", List.of());

        assertThat(mapper.map(TN, response).orElseThrow().getEstimatedDelivery()).isNull();
    }

    @Test
    @DisplayName("a state with no city produces no leading comma")
    void stateWithoutCityHasNoLeadingComma() {
        USPSTrackingResponse response = new USPSTrackingResponse(TN, "Delivered", null, null,
                List.of(event("2026-09-10T14:30:00-04:00", null, "NY", "In Transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation())
                .isEqualTo("NY");
    }

    @Test
    void missingCityAndStateProduceNoLocation() {
        USPSTrackingResponse response = new USPSTrackingResponse(TN, "Delivered", null, null,
                List.of(event("2026-09-10T14:30:00-04:00", null, null, "In Transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation()).isNull();
    }

    @Test
    @DisplayName("eventType is fed to a category lookup, so real event codes never match")
    void eventTypeCodeDoesNotMatchTheCategoryTable_knownMismatch() {
        // mapUSPS keys on human-readable CATEGORIES ("In Transit", "Delivered"), but the
        // per-event call passes eventType, which in the USPS API is a short code such as
        // "01". Every event status therefore degrades to UNKNOWN in practice, even though
        // the shipment-level statusCategory maps fine. Either map eventType through its
        // own table or feed eventDescription to mapFromDescription instead.
        USPSTrackingResponse response = new USPSTrackingResponse(TN, "Delivered", null, null,
                List.of(event("2026-09-10T14:30:00-04:00", "New York", "NY", "01")));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEvents().getFirst().getStatus()).isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void unparseableEventTimestampBecomesNull() {
        USPSTrackingResponse response = new USPSTrackingResponse(TN, "Delivered", null, null,
                List.of(event("09/10/2026 14:30", "New York", "NY", "In Transit")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }
}
