package com.trackingaggregator.client.fedex;

import java.util.List;

public record FedExTrackingRequest(
        List<TrackingInfo> trackingInfo,
        boolean includeDetailedScans
) {
    public record TrackingInfo(TrackingNumberInfo trackingNumberInfo) {}

    public record TrackingNumberInfo(String trackingNumber) {}

    public static FedExTrackingRequest of(String trackingNumber) {
        return new FedExTrackingRequest(
                List.of(new TrackingInfo(new TrackingNumberInfo(trackingNumber))),
                true
        );
    }
}
