package com.trackingaggregator.client.dhl;

import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class DHLResponseMapper {

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, DHLTrackingResponse response) {
        if (response == null || response.shipments() == null || response.shipments().isEmpty()) {
            return Optional.empty();
        }

        var shipment = response.shipments().getFirst();

        ShipmentStatus status = shipment.status() != null
                ? statusMapper.mapDHL(shipment.status().statusCode())
                : ShipmentStatus.UNKNOWN;

        LocalDate estimatedDelivery = parseDate(shipment.estimatedTimeOfDelivery());

        List<TrackingEvent> events = shipment.events() != null
                ? shipment.events().stream().map(this::mapEvent).toList()
                : Collections.emptyList();

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.DHL)
                .status(status)
                .estimatedDelivery(estimatedDelivery)
                .events(events));
    }

    private TrackingEvent mapEvent(DHLTrackingResponse.DHLEvent event) {
        String location = event.location() != null && event.location().address() != null
                ? event.location().address().addressLocality()
                : null;

        return new TrackingEvent()
                .timestamp(parseTimestamp(event.timestamp()))
                .location(location)
                .description(event.description())
                .status(statusMapper.mapDHL(event.statusCode()));
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) return null;
        try {
            return parseOffsetOrLocal(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate parseDate(String date) {
        if (date == null) return null;
        try {
            if (date.length() > 10) {
                return parseOffsetOrLocal(date).toLocalDate();
            }
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private OffsetDateTime parseOffsetOrLocal(String dateTime) {
        try {
            return OffsetDateTime.parse(dateTime);
        } catch (DateTimeParseException e) {
            // DHL sometimes omits the offset entirely; assume UTC in that case.
            return LocalDateTime.parse(dateTime).atOffset(ZoneOffset.UTC);
        }
    }
}
