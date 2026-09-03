package com.trackingaggregator.client.fedex;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/track/v1")
@RegisterRestClient(configKey = "fedex-api")
public interface FedExRestClient {

    @POST
    @Path("/trackingnumbers")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    FedExTrackingResponse trackShipment(
            @HeaderParam("Authorization") String bearerToken,
            FedExTrackingRequest request
    );
}
