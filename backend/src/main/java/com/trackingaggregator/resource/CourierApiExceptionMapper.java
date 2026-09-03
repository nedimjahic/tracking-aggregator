package com.trackingaggregator.resource;

import com.trackingaggregator.client.CourierApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class CourierApiExceptionMapper implements ExceptionMapper<CourierApiException> {

    @Override
    public Response toResponse(CourierApiException e) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "COURIER_UNAVAILABLE",
                        "message", "Courier %s is temporarily unavailable".formatted(e.getCourier()),
                        "trackingNumber", e.getTrackingNumber()))
                .build();
    }
}
