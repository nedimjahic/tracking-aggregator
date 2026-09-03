package com.trackingaggregator.client.usps;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/tracking/v3/tracking")
@RegisterRestClient(configKey = "usps-api")
public interface USPSRestClient {

    @GET
    @Path("/{trackingNumber}")
    USPSTrackingResponse trackShipment(
            @HeaderParam("Authorization") String bearerToken,
            @PathParam("trackingNumber") String trackingNumber,
            @QueryParam("expand") String expand
    );
}
