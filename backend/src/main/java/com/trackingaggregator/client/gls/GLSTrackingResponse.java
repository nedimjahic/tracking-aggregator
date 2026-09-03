package com.trackingaggregator.client.gls;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GLSTrackingResponse(List<GLSTuStatus> tuStatus) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GLSTuStatus(
            List<GLSHistoryEvent> history,
            GLSProgressBar progressBar
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GLSHistoryEvent(
            String date,
            String time,
            GLSAddress address,
            String evtDscr
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GLSAddress(String city, String countryCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GLSProgressBar(String statusInfo) {}
}
