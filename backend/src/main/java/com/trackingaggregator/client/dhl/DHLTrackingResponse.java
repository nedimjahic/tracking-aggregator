package com.trackingaggregator.client.dhl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DHLTrackingResponse(List<DHLShipment> shipments) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DHLShipment(
            DHLStatus status,
            String estimatedTimeOfDelivery,
            List<DHLEvent> events
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DHLStatus(String statusCode, String timestamp, DHLLocation location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DHLEvent(String timestamp, String description, String statusCode, DHLLocation location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DHLLocation(DHLAddress address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DHLAddress(String addressLocality) {}
}
