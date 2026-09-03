package com.trackingaggregator.client.fedex;

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
public class FedExResponseMapper {

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, FedExTrackingResponse response) {
        if (response == null || response.output() == null
                || response.output().completeTrackResults() == null
                || response.output().completeTrackResults().isEmpty()) {
            return Optional.empty();
        }

        var ctr = response.output().completeTrackResults().getFirst();
        if (ctr.trackResults() == null || ctr.trackResults().isEmpty()) {
            return Optional.empty();
        }

        var result = ctr.trackResults().getFirst();

        ShipmentStatus status = result.latestStatusDetail() != null
                ? statusMapper.mapFedEx(result.latestStatusDetail().derivedCode())
                : ShipmentStatus.UNKNOWN;

        LocalDate estimatedDelivery = null;
        if (result.estimatedDeliveryTimeWindow() != null
                && result.estimatedDeliveryTimeWindow().window() != null) {
            estimatedDelivery = parseDate(result.estimatedDeliveryTimeWindow().window().ends());
        }

        List<TrackingEvent> events = result.scanEvents() != null
                ? result.scanEvents().stream().map(this::mapScanEvent).toList()
                : Collections.emptyList();

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.FEDEX)
                .status(status)
                .estimatedDelivery(estimatedDelivery)
                .events(events));
    }

    private TrackingEvent mapScanEvent(FedExTrackingResponse.ScanEvent event) {
        String location = null;
        if (event.scanLocation() != null) {
            location = event.scanLocation().city();
            if (event.scanLocation().stateOrProvinceCode() != null) {
                location = location != null
                        ? location + ", " + event.scanLocation().stateOrProvinceCode()
                        : event.scanLocation().stateOrProvinceCode();
            }
        }

        return new TrackingEvent()
                .timestamp(parseTimestamp(event.date()))
                .location(location)
                .description(event.eventDescription())
                .status(statusMapper.mapFedEx(event.derivedStatus()));
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
            if (date.length() > 10) {
                return OffsetDateTime.parse(date).toLocalDate();
            }
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
