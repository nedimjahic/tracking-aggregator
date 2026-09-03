package com.trackingaggregator.client.ups;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UPSTrackingResponse(UPSTrackResponse trackResponse) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSTrackResponse(List<UPSShipment> shipment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSShipment(
            @JsonProperty("package") List<UPSPackage> packageList
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSPackage(
            List<UPSDeliveryDate> deliveryDate,
            UPSStatus currentStatus,
            List<UPSActivity> activity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSDeliveryDate(String date) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSStatus(String description, String code, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSActivity(
            String date,
            String time,
            UPSActivityLocation location,
            UPSActivityStatus status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSActivityLocation(UPSAddress address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSAddress(String city, String stateProvince, String country) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UPSActivityStatus(String description, String code, String type) {}
}
