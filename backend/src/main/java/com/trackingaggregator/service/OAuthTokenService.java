package com.trackingaggregator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class OAuthTokenService {

    private static final Logger LOG = Logger.getLogger(OAuthTokenService.class.getName());

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minusSeconds(60));
        }
    }

    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Inject
    ObjectMapper objectMapper;

    public String getToken(String courierKey, String tokenUrl, String clientId, String clientSecret) {
        CachedToken cached = tokenCache.get(courierKey);
        if (cached != null && !cached.isExpired()) {
            return cached.accessToken();
        }

        Object lock = locks.computeIfAbsent(courierKey, k -> new Object());
        synchronized (lock) {
            cached = tokenCache.get(courierKey);
            if (cached != null && !cached.isExpired()) {
                return cached.accessToken();
            }
            return fetchAndCacheToken(courierKey, tokenUrl, clientId, clientSecret);
        }
    }

    public void invalidate(String courierKey) {
        tokenCache.remove(courierKey);
        LOG.info("OAuth token invalidated for " + courierKey);
    }

    private String fetchAndCacheToken(String courierKey, String tokenUrl, String clientId, String clientSecret) {
        try {
            String credentials = Base64.getEncoder().encodeToString(
                    (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            String body = "grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Authorization", "Basic " + credentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OAuth token request failed with status %d for %s".formatted(
                        response.statusCode(), courierKey));
            }

            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.get("access_token").asText();
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;

            CachedToken token = new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn));
            tokenCache.put(courierKey, token);

            LOG.info("OAuth token obtained for " + courierKey + ", expires in " + expiresIn + "s");
            return accessToken;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to obtain OAuth token for " + courierKey, e);
        }
    }
}
