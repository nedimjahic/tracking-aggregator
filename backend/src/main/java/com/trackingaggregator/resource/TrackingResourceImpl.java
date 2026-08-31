package com.trackingaggregator.resource;

import com.trackingaggregator.api.DefaultApi;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingError;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.service.TrackingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class TrackingResourceImpl implements DefaultApi {

    @Inject
    TrackingService trackingService;

    @Override
    public TrackingResult trackShipment(String trackingNumber) {
        Courier courier = trackingService.detectCourier(trackingNumber)
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity(new TrackingError()
                                        .error(TrackingError.ErrorEnum.COURIER_NOT_DETECTED)
                                        .message("Unable to detect courier for tracking number")
                                        .trackingNumber(trackingNumber))
                                .build()));

        return trackingService.track(trackingNumber, courier)
                .orElseThrow(() -> new WebApplicationException(
                        Response.status(Response.Status.NOT_FOUND)
                                .entity(new TrackingError()
                                        .error(TrackingError.ErrorEnum.TRACKING_NOT_FOUND)
                                        .message("No shipment found for this tracking number")
                                        .trackingNumber(trackingNumber))
                                .build()));
    }
}
