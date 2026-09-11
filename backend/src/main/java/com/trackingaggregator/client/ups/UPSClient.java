package com.trackingaggregator.client.ups;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.service.OAuthTokenService;
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
import java.util.UUID;

@ApplicationScoped
public class UPSClient implements CourierClient {

    @RestClient
    UPSRestClient restClient;

    @Inject
    OAuthTokenService oAuthTokenService;

    @Inject
    UPSResponseMapper mapper;

    @ConfigProperty(name = "courier.ups.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "courier.ups.client-secret", defaultValue = "")
    String clientSecret;

    @ConfigProperty(name = "courier.ups.token-url",
            defaultValue = "https://onlinetools.ups.com/security/v1/oauth/token")
    String tokenUrl;

    @ConfigProperty(name = "courier.ups.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public Courier getCourier() {
        return Courier.UPS;
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
            String token = oAuthTokenService.getToken("ups", tokenUrl, clientId, clientSecret);
            UPSTrackingResponse response = restClient.trackShipment(
                    "Bearer " + token,
                    UUID.randomUUID().toString(),
                    "tracking-aggregator",
                    trackingNumber
            );
            return mapper.map(trackingNumber, response);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            if (e.getResponse().getStatus() == 401) {
                oAuthTokenService.invalidate("ups");
            }
            throw CourierApiException.fromWebApplicationException(Courier.UPS, trackingNumber, e);
        } catch (ProcessingException e) {
            throw CourierApiException.fromTransportFailure(Courier.UPS, trackingNumber, e);
        }
    }
}
