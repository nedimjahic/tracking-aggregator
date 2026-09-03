package com.trackingaggregator.client.usps;

import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class USPSResponseMapper {

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, USPSTrackingResponse response) {
        if (response == null) {
            return Optional.empty();
        }

        ShipmentStatus status = statusMapper.mapUSPS(response.statusCategory());

        LocalDate estimatedDelivery = parseDate(response.expectedDeliveryDate());

        List<TrackingEvent> events = response.trackingEvents() != null
                ? response.trackingEvents().stream().map(this::mapEvent).toList()
                : Collections.emptyList();

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.USPS)
                .status(status)
                .estimatedDelivery(estimatedDelivery)
                .events(events));
    }

    private TrackingEvent mapEvent(USPSTrackingResponse.USPSEvent event) {
        String location = event.eventCity();
        if (event.eventState() != null) {
            location = location != null ? location + ", " + event.eventState() : event.eventState();
        }

        return new TrackingEvent()
                .timestamp(parseTimestamp(event.eventTimestamp()))
                .location(location)
                .description(event.eventDescription())
                .status(statusMapper.mapUSPS(event.eventType()));
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) return null;
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate parseDate(String date) {
        if (date == null) return null;
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
