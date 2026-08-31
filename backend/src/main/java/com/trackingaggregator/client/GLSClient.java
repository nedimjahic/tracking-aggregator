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
public class GLSClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.GLS;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.GLS)
                .status(ShipmentStatus.IN_TRANSIT)
                .estimatedDelivery(LocalDate.now().plusDays(1))
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(2))
                                .location("Amsterdam, Netherlands")
                                .description("Parcel picked up")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusHours(12))
                                .location("Sorting center")
                                .description("Parcel in transit")
                                .status(ShipmentStatus.IN_TRANSIT)
                ));
        return Optional.of(result);
    }
}
