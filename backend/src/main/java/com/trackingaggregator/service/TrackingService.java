package com.trackingaggregator.service;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.entity.TrackingQuery;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class TrackingService {

    private static final Logger LOG = Logger.getLogger(TrackingService.class.getName());

    private final CourierDetectorService detectorService;
    private final TrackingCacheService cacheService;
    private final Map<Courier, CourierClient> clients;

    @Inject
    public TrackingService(CourierDetectorService detectorService,
                           TrackingCacheService cacheService,
                           Instance<CourierClient> clientInstances) {
        this.detectorService = detectorService;
        this.cacheService = cacheService;
        this.clients = clientInstances.stream()
                .collect(Collectors.toMap(CourierClient::getCourier, Function.identity()));
    }

    public Optional<Courier> detectCourier(String trackingNumber) {
        return detectorService.detect(trackingNumber);
    }

    public Optional<TrackingResult> track(String trackingNumber, Courier courier) {
        CourierClient client = clients.get(courier);
        if (client == null || !client.isEnabled()) {
            return Optional.empty();
        }

        Optional<TrackingResult> cached = cacheService.get(trackingNumber, courier);
        if (cached.isPresent()) {
            return cached;
        }

        try {
            Optional<TrackingResult> result = client.track(trackingNumber);

            result.ifPresent(r -> {
                cacheService.put(trackingNumber, courier, r);

                QuarkusTransaction.requiringNew().run(() -> {
                    TrackingQuery query = new TrackingQuery();
                    query.trackingNumber = trackingNumber;
                    query.courier = courier.toString();
                    query.status = r.getStatus().toString();
                    query.queriedAt = LocalDateTime.now();
                    query.persist();
                });
            });

            return result;
        } catch (CourierApiException e) {
            LOG.log(Level.WARNING, "Courier API failed, checking stale cache", e);
            Optional<TrackingResult> stale = cacheService.getStale(trackingNumber, courier);
            if (stale.isPresent()) {
                return stale;
            }
            throw e;
        }
    }
}
