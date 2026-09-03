package com.trackingaggregator.client.ups;

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
public class UPSResponseMapper {

    private static final DateTimeFormatter UPS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter UPS_TIME = DateTimeFormatter.ofPattern("HHmmss");

    @Inject
    StatusMapper statusMapper;

    public Optional<TrackingResult> map(String trackingNumber, UPSTrackingResponse response) {
        if (response == null || response.trackResponse() == null
                || response.trackResponse().shipment() == null
                || response.trackResponse().shipment().isEmpty()) {
            return Optional.empty();
        }

        var shipment = response.trackResponse().shipment().getFirst();
        if (shipment.packageList() == null || shipment.packageList().isEmpty()) {
            return Optional.empty();
        }

        var pkg = shipment.packageList().getFirst();

        ShipmentStatus status = pkg.currentStatus() != null
                ? statusMapper.mapUPS(pkg.currentStatus().type())
                : ShipmentStatus.UNKNOWN;

        LocalDate estimatedDelivery = null;
        if (pkg.deliveryDate() != null && !pkg.deliveryDate().isEmpty()) {
            estimatedDelivery = parseUPSDate(pkg.deliveryDate().getFirst().date());
        }

        List<TrackingEvent> events = pkg.activity() != null
                ? pkg.activity().stream().map(this::mapActivity).toList()
                : Collections.emptyList();

        return Optional.of(new TrackingResult()
                .trackingNumber(trackingNumber)
                .courier(Courier.UPS)
                .status(status)
                .estimatedDelivery(estimatedDelivery)
                .events(events));
    }

    private TrackingEvent mapActivity(UPSTrackingResponse.UPSActivity activity) {
        String location = null;
        if (activity.location() != null && activity.location().address() != null) {
            var addr = activity.location().address();
            location = addr.city();
            if (addr.stateProvince() != null) {
                location = location != null ? location + ", " + addr.stateProvince() : addr.stateProvince();
            }
        }

        ShipmentStatus status = activity.status() != null
                ? statusMapper.mapUPS(activity.status().type())
                : ShipmentStatus.UNKNOWN;

        return new TrackingEvent()
                .timestamp(parseUPSDateTime(activity.date(), activity.time()))
                .location(location)
                .description(activity.status() != null ? activity.status().description() : null)
                .status(status);
    }

    private OffsetDateTime parseUPSDateTime(String date, String time) {
        if (date == null) return null;
        try {
            LocalDate d = LocalDate.parse(date, UPS_DATE);
            LocalTime t = time != null ? LocalTime.parse(time, UPS_TIME) : LocalTime.MIDNIGHT;
            return OffsetDateTime.of(d, t, ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDate parseUPSDate(String date) {
        if (date == null) return null;
        try {
            return LocalDate.parse(date, UPS_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
