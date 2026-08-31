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
public class DPDClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.DPD;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.DPD)
                .status(ShipmentStatus.OUT_FOR_DELIVERY)
                .estimatedDelivery(LocalDate.now())
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(1))
                                .location("Depot")
                                .description("Parcel received at depot")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusHours(2))
                                .location("Local delivery")
                                .description("Out for delivery")
                                .status(ShipmentStatus.OUT_FOR_DELIVERY)
                ));
        return Optional.of(result);
    }
}
