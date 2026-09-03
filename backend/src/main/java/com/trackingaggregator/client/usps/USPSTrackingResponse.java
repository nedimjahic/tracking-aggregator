package com.trackingaggregator.client.usps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record USPSTrackingResponse(
        String trackingNumber,
        String statusCategory,
        String statusSummary,
        String expectedDeliveryDate,
        List<USPSEvent> trackingEvents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record USPSEvent(
            String eventTimestamp,
            String eventCity,
            String eventState,
            String eventDescription,
            String eventType
    ) {}
}
