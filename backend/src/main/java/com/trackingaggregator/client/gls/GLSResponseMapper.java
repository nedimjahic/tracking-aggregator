package com.trackingaggregator.client.gls;

import com.trackingaggregator.client.mapper.StatusMapper;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.ShipmentStatus;
import com.trackingaggregator.model.TrackingEvent;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GLSResponseMapper {

    private static final DateTimeFormatter GLS_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter GLS_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, GLSTrackingResponse response) {
        if (response == null || response.tuStatus() == null || response.tuStatus().isEmpty()) {
            return Optional.empty();
        }

        var tuStatus = response.tuStatus().getFirst();

        ShipmentStatus status = tuStatus.progressBar() != null
                ? statusMapper.mapFromDescription(tuStatus.progressBar().statusInfo())
                : ShipmentStatus.UNKNOWN;

        List<TrackingEvent> events = tuStatus.history() != null
                ? tuStatus.history().stream().map(this::mapEvent).toList()
                : Collections.emptyList();

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.GLS)
                .status(status)
                .events(events));
    }

    private TrackingEvent mapEvent(GLSTrackingResponse.GLSHistoryEvent event) {
        String location = event.address() != null ? event.address().city() : null;

        return new TrackingEvent()
                .timestamp(parseDateTime(event.date(), event.time()))
                .location(location)
                .description(event.evtDscr())
                .status(statusMapper.mapFromDescription(event.evtDscr()));
    }

    private OffsetDateTime parseDateTime(String date, String time) {
        if (date == null) return null;
        try {
            LocalDate d = LocalDate.parse(date, GLS_DATE);
            LocalTime t = time != null ? LocalTime.parse(time, GLS_TIME) : LocalTime.MIDNIGHT;
            return OffsetDateTime.of(d, t, ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
