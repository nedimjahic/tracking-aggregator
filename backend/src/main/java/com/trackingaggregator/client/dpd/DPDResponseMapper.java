package com.trackingaggregator.client.dpd;

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
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class DPDResponseMapper {

    private static final Map<String, ShipmentStatus> DPD_SCAN_TYPES = Map.of(
            "PICKUP", ShipmentStatus.PICKED_UP,
            "IN_TRANSIT", ShipmentStatus.IN_TRANSIT,
            "HUB", ShipmentStatus.IN_TRANSIT,
            "OUT_FOR_DELIVERY", ShipmentStatus.OUT_FOR_DELIVERY,
            "DELIVERED", ShipmentStatus.DELIVERED
    );

    private static final DateTimeFormatter DPD_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DPD_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, DPDTrackingResponse response) {
        if (response == null || response.parcellifecycleResponse() == null
                || response.parcellifecycleResponse().parcelLifeCycleData() == null) {
            return Optional.empty();
        }

        var data = response.parcellifecycleResponse().parcelLifeCycleData();

        ShipmentStatus status = data.shipmentInfo() != null
                ? statusMapper.mapFromDescription(data.shipmentInfo().status())
                : ShipmentStatus.UNKNOWN;

        List<TrackingEvent> events = Collections.emptyList();
        if (data.scanInfo() != null && data.scanInfo().scan() != null) {
            events = data.scanInfo().scan().stream().map(this::mapScan).toList();
        }

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.DPD)
                .status(status)
                .events(events));
    }

    private TrackingEvent mapScan(DPDTrackingResponse.DPDScan scan) {
        ShipmentStatus status = scan.scanType() != null
                ? DPD_SCAN_TYPES.getOrDefault(scan.scanType().toUpperCase(), ShipmentStatus.UNKNOWN)
                : ShipmentStatus.UNKNOWN;

        String description = scan.scanDescription() != null ? scan.scanDescription().content() : null;

        return new TrackingEvent()
                .timestamp(parseDateTime(scan.date(), scan.time()))
                .description(description)
                .status(status);
    }

    private OffsetDateTime parseDateTime(String date, String time) {
        if (date == null) return null;
        try {
            LocalDate d = LocalDate.parse(date, DPD_DATE);
            LocalTime t = time != null ? LocalTime.parse(time, DPD_TIME) : LocalTime.MIDNIGHT;
            return OffsetDateTime.of(d, t, ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
