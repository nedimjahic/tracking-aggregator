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
public class DHLClient implements CourierClient {

    @Override
    public Courier getCourier() {
        return Courier.DHL;
    }

    @Override
    public Optional<TrackingResult> track(String trackingNumber) {
        TrackingResult result = new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.DHL)
                .status(ShipmentStatus.IN_TRANSIT)
                .estimatedDelivery(LocalDate.now().plusDays(2))
                .events(List.of(
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(3))
                                .location("Leipzig, Germany")
                                .description("Shipment picked up")
                                .status(ShipmentStatus.PICKED_UP),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusDays(1))
                                .location("Frankfurt, Germany")
                                .description("Processed at gateway")
                                .status(ShipmentStatus.IN_TRANSIT),
                        new TrackingEvent()
                                .timestamp(OffsetDateTime.now().minusHours(8))
                                .location("Destination country")
                                .description("Arrived at customs")
                                .status(ShipmentStatus.IN_TRANSIT)
                ));
        return Optional.of(result);
    }
}
