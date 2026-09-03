package com.trackingaggregator.client.dhl;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/track")
@RegisterRestClient(configKey = "dhl-api")
public interface DHLRestClient {

    @GET
    @Path("/shipments")
    DHLTrackingResponse trackShipment(
            @HeaderParam("DHL-API-Key") String apiKey,
            @QueryParam("trackingNumber") String trackingNumber
    );
}
