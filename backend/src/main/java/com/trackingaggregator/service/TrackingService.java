package com.trackingaggregator.service;

import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.entity.TrackingQuery;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class TrackingService {

    private final CourierDetectorService detectorService;
    private final Map<Courier, CourierClient> clients;

    @Inject
    public TrackingService(CourierDetectorService detectorService, Instance<CourierClient> clientInstances) {
        this.detectorService = detectorService;
        this.clients = clientInstances.stream()
                .collect(Collectors.toMap(CourierClient::getCourier, Function.identity()));
    }

    public Optional<Courier> detectCourier(String trackingNumber) {
        return detectorService.detect(trackingNumber);
    }

    @Transactional
    public Optional<TrackingResult> track(String trackingNumber, Courier courier) {
        CourierClient client = clients.get(courier);
        if (client == null) {
            return Optional.empty();
        }

        Optional<TrackingResult> result = client.track(trackingNumber);

        result.ifPresent(r -> {
            TrackingQuery query = new TrackingQuery();
            query.trackingNumber = trackingNumber;
            query.courier = courier.toString();
            query.status = r.getStatus().toString();
            query.queriedAt = LocalDateTime.now();
            query.persist();
        });

        return result;
    }
}
