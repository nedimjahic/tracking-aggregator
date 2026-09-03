package com.trackingaggregator.client.ups;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/api/track/v1")
@RegisterRestClient(configKey = "ups-api")
public interface UPSRestClient {

    @GET
    @Path("/details/{trackingNumber}")
    UPSTrackingResponse trackShipment(
            @HeaderParam("Authorization") String bearerToken,
            @HeaderParam("transId") String transId,
            @HeaderParam("transactionSrc") String transactionSrc,
            @PathParam("trackingNumber") String trackingNumber
    );
}
