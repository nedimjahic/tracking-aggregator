package com.trackingaggregator.client.gls;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/public/v1/tracking")
@RegisterRestClient(configKey = "gls-api")
public interface GLSRestClient {

    @GET
    @Path("/references/{trackingNumber}")
    GLSTrackingResponse trackShipment(
            @HeaderParam("X-API-Key") String apiKey,
            @PathParam("trackingNumber") String trackingNumber
    );
}
