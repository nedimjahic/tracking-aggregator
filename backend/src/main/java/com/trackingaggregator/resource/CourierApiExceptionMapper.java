package com.trackingaggregator.resource;

import com.trackingaggregator.client.CourierApiException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Map;

@Provider
public class CourierApiExceptionMapper implements ExceptionMapper<CourierApiException> {

    @Override
    public Response toResponse(CourierApiException e) {
        // HashMap, not Map.of: a null tracking number must not blow up the mapper itself.
        Map<String, Object> body = new HashMap<>();
        body.put("error", "COURIER_UNAVAILABLE");
        body.put("message", "Courier %s is temporarily unavailable".formatted(e.getCourier()));
        body.put("trackingNumber", e.getTrackingNumber());

        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
