package com.trackingaggregator.client.dpd;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DPDTrackingResponse(DPDParcelLifeCycleResponse parcellifecycleResponse) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDParcelLifeCycleResponse(DPDParcelLifeCycleData parcelLifeCycleData) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDParcelLifeCycleData(
            DPDShipmentInfo shipmentInfo,
            DPDScanInfo scanInfo
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDShipmentInfo(String status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDScanInfo(List<DPDScan> scan) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDScan(
            String date,
            String time,
            DPDScanDescription scanDescription,
            String scanType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DPDScanDescription(String content) {}
}
