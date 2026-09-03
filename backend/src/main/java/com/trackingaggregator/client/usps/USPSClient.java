package com.trackingaggregator.client.usps;

import com.trackingaggregator.client.CourierApiException;
import com.trackingaggregator.client.CourierClient;
import com.trackingaggregator.model.Courier;
import com.trackingaggregator.model.TrackingResult;
import com.trackingaggregator.service.OAuthTokenService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
public class USPSClient implements CourierClient {

    @RestClient
    USPSRestClient restClient;

    @Inject
    OAuthTokenService oAuthTokenService;

    @Inject
    USPSResponseMapper mapper;

    @ConfigProperty(name = "courier.usps.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "courier.usps.client-secret", defaultValue = "")
    String clientSecret;

    @ConfigProperty(name = "courier.usps.token-url",
            defaultValue = "https://api.usps.com/oauth2/v3/token")
    String tokenUrl;

    @ConfigProperty(name = "courier.usps.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public Courier getCourier() {
        return Courier.USPS;
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
            String token = oAuthTokenService.getToken("usps", tokenUrl, clientId, clientSecret);
            USPSTrackingResponse response = restClient.trackShipment(
                    "Bearer " + token,
                    trackingNumber,
                    "DETAIL"
            );
            return mapper.map(trackingNumber, response);
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() == 404) {
                return Optional.empty();
            }
            if (e.getResponse().getStatus() == 401) {
                oAuthTokenService.invalidate("usps");
            }
            throw new CourierApiException(Courier.USPS, trackingNumber, e.getResponse().getStatus(), e);
        }
    }
}
