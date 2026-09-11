package com.trackingaggregator.client.ups;

import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSActivity;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSActivityLocation;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSActivityStatus;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSAddress;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSDeliveryDate;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSPackage;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSShipment;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSStatus;
import com.trackingaggregator.client.ups.UPSTrackingResponse.UPSTrackResponse;
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
class UPSResponseMapperTest {

    private static final String TN = "1Z999AA10123456784";

    private UPSResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new UPSResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static UPSTrackingResponse of(UPSPackage pkg) {
        return new UPSTrackingResponse(
                new UPSTrackResponse(List.of(new UPSShipment(pkg == null ? null : List.of(pkg)))));
    }

    private static UPSPackage pkg(String statusType, String deliveryDate, List<UPSActivity> activity) {
        return new UPSPackage(
                deliveryDate == null ? List.of() : List.of(new UPSDeliveryDate(deliveryDate)),
                statusType == null ? null : new UPSStatus("Delivered", "011", statusType),
                activity);
    }

    private static UPSActivity activity(String date, String time, String city, String state, String type) {
        UPSActivityLocation location = (city == null && state == null)
                ? null
                : new UPSActivityLocation(new UPSAddress(city, state, "US"));
        return new UPSActivity(date, time, location,
                type == null ? null : new UPSActivityStatus("Arrived at facility", "AR", type));
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    void nullTrackResponseIsEmpty() {
        assertThat(mapper.map(TN, new UPSTrackingResponse(null))).isEmpty();
    }

    @Test
    void nullOrEmptyShipmentIsEmpty() {
        assertThat(mapper.map(TN, new UPSTrackingResponse(new UPSTrackResponse(null)))).isEmpty();
        assertThat(mapper.map(TN, new UPSTrackingResponse(new UPSTrackResponse(List.of())))).isEmpty();
    }

    @Test
    void nullOrEmptyPackageListIsEmpty() {
        assertThat(mapper.map(TN, of(null))).isEmpty();
        assertThat(mapper.map(TN, new UPSTrackingResponse(
                new UPSTrackResponse(List.of(new UPSShipment(List.of())))))).isEmpty();
    }

    @Test
    void mapsAFullyPopulatedPackage() {
        UPSTrackingResponse response = of(pkg("D", "20260915",
                List.of(activity("20260910", "143000", "Louisville", "KY", "I"))));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.UPS);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
        assertThat(result.getEstimatedDelivery()).isEqualTo(LocalDate.of(2026, 9, 15));

        var event = result.getEvents().getFirst();
        assertThat(event.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-09-10T14:30:00Z"));
        assertThat(event.getLocation()).isEqualTo("Louisville, KY");
        assertThat(event.getDescription()).isEqualTo("Arrived at facility");
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
    }

    @Test
    @DisplayName("a missing activity time defaults to midnight UTC")
    void missingTimeDefaultsToMidnight() {
        UPSTrackingResponse response = of(pkg("I", null,
                List.of(activity("20260910", null, "Louisville", "KY", "I"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp())
                .isEqualTo(OffsetDateTime.parse("2026-09-10T00:00:00Z"));
    }

    @Test
    @DisplayName("UPS dates are yyyyMMdd - an ISO date does not parse")
    void isoFormattedActivityDateIsRejected() {
        UPSTrackingResponse response = of(pkg("I", null,
                List.of(activity("2026-09-10", "143000", "Louisville", "KY", "I"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }

    @Test
    @DisplayName("a state with no city produces no leading comma")
    void stateWithoutCityHasNoLeadingComma() {
        UPSTrackingResponse response = of(pkg("I", null,
                List.of(activity("20260910", "143000", null, "KY", "I"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation())
                .isEqualTo("KY");
    }

    @Test
    void missingCityAndStateProduceNoLocation() {
        UPSTrackingResponse response = of(pkg("I", null,
                List.of(activity("20260910", "143000", null, null, "I"))));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation()).isNull();
    }

    @Test
    void nullCurrentStatusBecomesUnknown() {
        assertThat(mapper.map(TN, of(pkg(null, null, List.of()))).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void nullActivityStatusBecomesUnknownWithNoDescription() {
        UPSTrackingResponse response = of(pkg("I", null,
                List.of(activity("20260910", "143000", "Louisville", "KY", null))));

        var event = mapper.map(TN, response).orElseThrow().getEvents().getFirst();
        assertThat(event.getStatus()).isEqualTo(ShipmentStatus.UNKNOWN);
        assertThat(event.getDescription()).isNull();
    }

    @Test
    void nullActivityListBecomesEmptyEvents() {
        assertThat(mapper.map(TN, of(pkg("I", null, null))).orElseThrow().getEvents())
                .isNotNull().isEmpty();
    }

    @Test
    void emptyDeliveryDateListLeavesEstimatedDeliveryNull() {
        assertThat(mapper.map(TN, of(pkg("I", null, List.of()))).orElseThrow().getEstimatedDelivery())
                .isNull();
    }
}
