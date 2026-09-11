package com.trackingaggregator.support;

import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Sample tracking numbers and {@link TrackingResult} builders shared across the suite. */
public final class Fixtures {

    /** Tracking numbers chosen so each one resolves to exactly the intended courier. */
    public static final String DHL_NUMBER = "1234567890";             // 10 digits
    public static final String UPS_NUMBER = "1Z999AA10123456784";     // 1Z + 16
    public static final String FEDEX_NUMBER = "123456789012";         // 12 digits
    public static final String USPS_NUMBER = "12345678901234567890";  // 20 digits
    public static final String GLS_NUMBER = "12345678901";            // 11 digits
    public static final String DPD_NUMBER = "12345678901234";         // 14 digits

    private Fixtures() {
    }

    /** A fully populated, DELIVERED result with two events. */
    public static TrackingResult delivered(String trackingNumber, Courier courier) {
        return new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(courier)
                .status(ShipmentStatus.DELIVERED)
                .estimatedDelivery(LocalDate.of(2026, 9, 15))
                .events(List.of(
                        event("2026-09-10T14:30:00+02:00", "Frankfurt",
                                "Processed at facility", ShipmentStatus.IN_TRANSIT),
                        event("2026-09-15T09:05:00+02:00", "Berlin",
                                "Delivered", ShipmentStatus.DELIVERED)));
    }

    public static TrackingResult withStatus(String trackingNumber, Courier courier, ShipmentStatus status) {
        return new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(courier)
                .status(status)
                .events(List.of());
    }

    /** Reproduces the null-status scenario that NPEs in TrackingService.track. */
    public static TrackingResult withNullStatus(String trackingNumber, Courier courier) {
        return new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(courier)
                .status(null)
                .events(List.of());
    }

    public static TrackingEvent event(String isoTimestamp, String location,
                                      String description, ShipmentStatus status) {
        return new TrackingEvent()
                .timestamp(isoTimestamp == null ? null : OffsetDateTime.parse(isoTimestamp))
                .location(location)
                .description(description)
                .status(status);
    }
}
