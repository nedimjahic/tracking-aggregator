package com.trackingaggregator.service;

import com.trackingaggregator.model.Courier;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@ApplicationScoped
public class CourierDetectorService {

    private static final Map<Courier, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        PATTERNS.put(Courier.UPS, Pattern.compile("^1Z[A-Z0-9]{16}$", Pattern.CASE_INSENSITIVE));
        PATTERNS.put(Courier.FEDEX, Pattern.compile("^\\d{12}(\\d{3})?$"));
        PATTERNS.put(Courier.USPS, Pattern.compile("^\\d{20,22}$"));
        PATTERNS.put(Courier.DHL, Pattern.compile("^(\\d{10}|JJD\\d{18})$", Pattern.CASE_INSENSITIVE));
        PATTERNS.put(Courier.DPD, Pattern.compile("^\\d{14}$"));
        PATTERNS.put(Courier.GLS, Pattern.compile("^\\d{11,12}$"));
    }

    public Optional<Courier> detect(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            return Optional.empty();
        }
        String normalized = trackingNumber.trim().toUpperCase();
        return PATTERNS.entrySet().stream()
                .filter(entry -> entry.getValue().matcher(normalized).matches())
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
