package com.trackingaggregator.client.fedex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FedExTrackingResponse(FedExOutput output) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FedExOutput(List<CompleteTrackResult> completeTrackResults) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CompleteTrackResult(List<TrackResult> trackResults) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrackResult(
            LatestStatusDetail latestStatusDetail,
            EstimatedDeliveryTimeWindow estimatedDeliveryTimeWindow,
            List<ScanEvent> scanEvents
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LatestStatusDetail(String code, String description, String derivedCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EstimatedDeliveryTimeWindow(DeliveryWindow window) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeliveryWindow(String ends) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScanEvent(
            String date,
            String eventDescription,
            String derivedStatus,
            ScanLocation scanLocation
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScanLocation(String city, String stateOrProvinceCode, String countryCode) {}
}
