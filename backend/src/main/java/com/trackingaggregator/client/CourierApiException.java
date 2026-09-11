package com.trackingaggregator.client;

import com.trackingaggregator.model.Courier;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;

public class CourierApiException extends RuntimeException {

    /** Reported when the underlying failure has no real upstream HTTP status to report. */
    public static final int BAD_GATEWAY = 502;

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

    /**
     * Builds a {@link CourierApiException} from a {@link WebApplicationException} raised by the
     * REST client. A response status of 200 does not mean the call actually failed at the HTTP
     * level - it means the REST client received a successful response it could not deserialize
     * (a malformed body). Reporting httpStatus 200 in that case would be misleading, so it is
     * reported as {@link #BAD_GATEWAY} instead.
     */
    public static CourierApiException fromWebApplicationException(
            Courier courier, String trackingNumber, WebApplicationException e) {
        int status = e.getResponse().getStatus();
        return new CourierApiException(courier, trackingNumber, status == 200 ? BAD_GATEWAY : status, e);
    }

    /**
     * Builds a {@link CourierApiException} from a transport-level failure (connection reset,
     * timeout, DNS failure, ...) that would otherwise escape as a bare {@link ProcessingException},
     * bypassing both {@code CourierApiExceptionMapper}'s 503 response and the stale-cache fallback.
     */
    public static CourierApiException fromTransportFailure(
            Courier courier, String trackingNumber, ProcessingException e) {
        return new CourierApiException(courier, trackingNumber, e);
    }

    public Courier getCourier() { return courier; }
    public String getTrackingNumber() { return trackingNumber; }
    public int getHttpStatus() { return httpStatus; }
}
