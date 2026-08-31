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
public class UPSClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.UPS;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.UPS)
                .status(ShipmentStatus.IN_TRANSIT)
                .estimatedDelivery(LocalDate.now().plusDays(3))
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(2))
                                .location("Louisville, KY")
                                .description("Shipment picked up")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(1))
                                .location("Columbus, OH")
                                .description("In transit to destination")
                                .status(ShipmentStatus.IN_TRANSIT),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusHours(6))
                                .location("Pittsburgh, PA")
                                .description("Arrived at facility")
                                .status(ShipmentStatus.IN_TRANSIT)
                ));
        return Optional.of(result);
    }
}
