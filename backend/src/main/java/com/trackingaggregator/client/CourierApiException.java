package com.trackingaggregator.client;

import com.trackingaggregator.model.Courier;

public class CourierApiException extends RuntimeException {

    private final Courier courier;
    private final String trackingNumber;
    private final int httpStatus;

    public CourierApiException(Courier courier, String trackingNumber, Throwable cause) {
        this(courier, trackingNumber, 0, cause);
    }

    public CourierApiException(Courier courier, String trackingNumber, int httpStatus, Throwable cause) {
        super("Courier API error for %s tracking %s (HTTP %d)".formatted(courier, trackingNumber, httpStatus), cause);
        this.courier = courier;
        this.trackingNumber = trackingNumber;
        this.httpStatus = httpStatus;
    }

    public Courier getCourier() { return courier; }
    public String getTrackingNumber() { return trackingNumber; }
    public int getHttpStatus() { return httpStatus; }
}
