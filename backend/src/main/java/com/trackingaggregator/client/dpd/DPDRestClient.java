package com.trackingaggregator.client.dpd;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/rest/plc/en_US")
@RegisterRestClient(configKey = "dpd-api")
public interface DPDRestClient {

    @GET
    @Path("/shipment/{trackingNumber}")
    DPDTrackingResponse trackShipment(
            @PathParam("trackingNumber") String trackingNumber
    );
}
