package com.trackingaggregator.client;

import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;

import java.util.Optional;

public interface CourierClient {

    Courier getCourier();

    Optional<TrackingResult> track(String trackingNumber);
}
