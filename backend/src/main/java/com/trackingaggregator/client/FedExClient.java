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
public class FedExClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.FEDEX;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.FEDEX)
                .status(ShipmentStatus.OUT_FOR_DELIVERY)
                .estimatedDelivery(LocalDate.now())
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(3))
                                .location("Memphis, TN")
                                .description("Shipment received")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(1))
                                .location("Indianapolis, IN")
                                .description("In transit")
                                .status(ShipmentStatus.IN_TRANSIT),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusHours(3))
                                .location("Local facility")
                                .description("Out for delivery")
                                .status(ShipmentStatus.OUT_FOR_DELIVERY)
                ));
        return Optional.of(result);
    }
}
