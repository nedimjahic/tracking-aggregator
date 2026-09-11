package com.trackingaggregator.client.dpd;

import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDParcelLifeCycleData;
import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDParcelLifeCycleResponse;
import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDScan;
import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDScanDescription;
import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDScanInfo;
import com.trackingaggregator.client.dpd.DPDTrackingResponse.DPDShipmentInfo;
import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** In the production package so the package-private StatusMapper field can be assigned. */
class DPDResponseMapperTest {

    private static final String TN = "12345678901234";

    private DPDResponseMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DPDResponseMapper();
        mapper.statusMapper = new StatusMapper();
    }

    private static DPDTrackingResponse of(String shipmentStatus, List<DPDScan> scans) {
        return new DPDTrackingResponse(new DPDParcelLifeCycleResponse(new DPDParcelLifeCycleData(
                shipmentStatus == null ? null : new DPDShipmentInfo(shipmentStatus),
                scans == null ? null : new DPDScanInfo(scans))));
    }

    private static DPDScan scan(String date, String time, String description, String scanType) {
        return new DPDScan(date, time,
                description == null ? null : new DPDScanDescription(description), scanType);
    }

    @Test
    void nullResponseIsEmpty() {
        assertThat(mapper.map(TN, null)).isEmpty();
    }

    @Test
    void nullLifecycleResponseIsEmpty() {
        assertThat(mapper.map(TN, new DPDTrackingResponse(null))).isEmpty();
    }

    @Test
    void nullLifecycleDataIsEmpty() {
        assertThat(mapper.map(TN, new DPDTrackingResponse(new DPDParcelLifeCycleResponse(null))))
                .isEmpty();
    }

    @Test
    void mapsAFullyPopulatedLifecycle() {
        // Day 25 is deliberate: it is the only way a dd/MM vs MM/dd mix-up fails loudly.
        DPDTrackingResponse response = of("Delivered",
                List.of(scan("25/12/2026", "14:30:00", "Parcel delivered", "DELIVERED")));

        TrackingResult result = mapper.map(TN, response).orElseThrow();

        assertThat(result.getTrackingNumber()).isEqualTo(TN);
        assertThat(result.getCourier()).isEqualTo(Courier.DPD);
        assertThat(result.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);

        var mapped = result.getEvents().getFirst();
        assertThat(mapped.getTimestamp()).isEqualTo(OffsetDateTime.parse("2026-12-25T14:30:00Z"));
        assertThat(mapped.getDescription()).isEqualTo("Parcel delivered");
        assertThat(mapped.getStatus()).isEqualTo(ShipmentStatus.DELIVERED);
    }

    @ParameterizedTest(name = "scanType {0} -> {1}")
    @CsvSource({
            "PICKUP, PICKED_UP",
            "IN_TRANSIT, IN_TRANSIT",
            "HUB, IN_TRANSIT",
            "OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
            "DELIVERED, DELIVERED",
            // the mapper upper-cases before the lookup
            "delivered, DELIVERED",
            "out_for_delivery, OUT_FOR_DELIVERY",
    })
    void mapsScanTypes(String scanType, ShipmentStatus expected) {
        DPDTrackingResponse response = of("In transit",
                List.of(scan("25/12/2026", "14:30:00", "Scan", scanType)));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getStatus())
                .isEqualTo(expected);
    }

    @Test
    void unknownOrMissingScanTypeBecomesUnknown() {
        DPDTrackingResponse unknown = of("In transit",
                List.of(scan("25/12/2026", "14:30:00", "Scan", "FOO")));
        assertThat(mapper.map(TN, unknown).orElseThrow().getEvents().getFirst().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);

        DPDTrackingResponse missing = of("In transit",
                List.of(scan("25/12/2026", "14:30:00", "Scan", null)));
        assertThat(mapper.map(TN, missing).orElseThrow().getEvents().getFirst().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    @DisplayName("DPD events never carry a location")
    void eventsNeverHaveALocation() {
        // mapScan builds the TrackingEvent without ever calling .location(), and the DPD
        // DTO carries no address field to populate it from. Every DPD event therefore
        // reaches the client with a null location while the other five couriers set one.
        DPDTrackingResponse response = of("Delivered",
                List.of(scan("25/12/2026", "14:30:00", "Parcel delivered", "DELIVERED")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getLocation())
                .isNull();
    }

    @Test
    @DisplayName("DPD never populates estimatedDelivery")
    void estimatedDeliveryIsNeverSet() {
        assertThat(mapper.map(TN, of("Delivered", List.of())).orElseThrow().getEstimatedDelivery())
                .isNull();
    }

    @Test
    void nullShipmentInfoBecomesUnknown() {
        assertThat(mapper.map(TN, of(null, List.of())).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.UNKNOWN);
    }

    @Test
    void nullScanInfoOrScanListBecomesEmptyEvents() {
        assertThat(mapper.map(TN, of("Delivered", null)).orElseThrow().getEvents())
                .isNotNull().isEmpty();

        DPDTrackingResponse nullScanList = new DPDTrackingResponse(
                new DPDParcelLifeCycleResponse(new DPDParcelLifeCycleData(
                        new DPDShipmentInfo("Delivered"), new DPDScanInfo(null))));
        assertThat(mapper.map(TN, nullScanList).orElseThrow().getEvents()).isNotNull().isEmpty();
    }

    @Test
    void nullScanDescriptionProducesNoDescription() {
        DPDTrackingResponse response = of("Delivered",
                List.of(scan("25/12/2026", "14:30:00", null, "DELIVERED")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getDescription())
                .isNull();
    }

    @Test
    @DisplayName("DPD dates are dd/MM/yyyy - an ISO date does not parse")
    void isoFormattedDateIsRejected() {
        DPDTrackingResponse response = of("Delivered",
                List.of(scan("2026-09-10", "14:30:00", "Scan", "DELIVERED")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp()).isNull();
    }

    @Test
    void missingTimeDefaultsToMidnight() {
        DPDTrackingResponse response = of("Delivered",
                List.of(scan("25/12/2026", null, "Scan", "DELIVERED")));

        assertThat(mapper.map(TN, response).orElseThrow().getEvents().getFirst().getTimestamp())
                .isEqualTo(OffsetDateTime.parse("2026-12-25T00:00:00Z"));
    }
}
