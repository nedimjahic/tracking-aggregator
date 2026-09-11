package com.trackingaggregator.client.dhl;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

@ApplicationScoped
public class DHLClient implements CourierClient {

    @RestClient
    DHLRestClient restClient;

    @ConfigProperty(name = "courier.dhl.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "courier.dhl.enabled", defaultValue = "true")
    boolean enabled;

    @Inject
    DHLResponseMapper mapper;

    @Override
    public Courier getCourier() {
        return Courier.DHL;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    @Timeout(value = 10, unit = ChronoUnit.SECONDS)
    @Retry(maxRetries = 2, delay = 1, delayUnit = ChronoUnit.SECONDS,
            retryOn = {java.net.SocketTimeoutException.class, jakarta.ws.rs.ProcessingException.class,
                    CourierApiException.class, TimeoutException.class})
    @CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5,
            delay = 30, delayUnit = ChronoUnit.SECONDS)
    public Optional<TrackingResult> track(String trackingNumber) {
        try {
            DHLTrackingResponse response = restClient.trackShipment(apiKey, trackingNumber);
            return mapper.map(trackingNumber, response);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            throw CourierApiException.fromWebApplicationException(Courier.DHL, trackingNumber, e);
        } catch (ProcessingException e) {
            throw CourierApiException.fromTransportFailure(Courier.DHL, trackingNumber, e);
        }
    }
}
