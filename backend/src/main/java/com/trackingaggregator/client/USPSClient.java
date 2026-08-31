package com.trackingaggregator.client;

import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class USPSClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.USPS;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.USPS)
                .status(ShipmentStatus.DELIVERED)
                .estimatedDelivery(LocalDate.now().minusDays(1))
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(4))
                                .location("Origin Post Office")
                                .description("Accepted at USPS origin facility")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(2))
                                .location("Regional Distribution Center")
                                .description("In transit to destination")
                                .status(ShipmentStatus.IN_TRANSIT),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(1))
                                .location("Delivered")
                                .description("Delivered, left with individual")
                                .status(ShipmentStatus.DELIVERED)
                ));
        return Optional.of(result);
    }
}
