package com.trackingaggregator.client.mapper;

import com.trackingaggregator.model.ShipmentStatus;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class StatusMapper {

    private static final Map<String, ShipmentStatus> DHL_STATUS = Map.of(
            "pre-transit", ShipmentStatus.PICKED_UP,
            "transit", ShipmentStatus.IN_TRANSIT,
            "out-for-delivery", ShipmentStatus.OUT_FOR_DELIVERY,
            "delivered", ShipmentStatus.DELIVERED,
            "failure", ShipmentStatus.FAILED_ATTEMPT
    );

    private static final Map<String, ShipmentStatus> UPS_STATUS = Map.of(
            "P", ShipmentStatus.PICKED_UP,
            "I", ShipmentStatus.IN_TRANSIT,
            "O", ShipmentStatus.OUT_FOR_DELIVERY,
            "D", ShipmentStatus.DELIVERED,
            "X", ShipmentStatus.FAILED_ATTEMPT,
            "M", ShipmentStatus.PICKED_UP
    );

    private static final Map<String, ShipmentStatus> FEDEX_STATUS = Map.of(
            "PU", ShipmentStatus.PICKED_UP,
            "IT", ShipmentStatus.IN_TRANSIT,
            "OD", ShipmentStatus.OUT_FOR_DELIVERY,
            "DL", ShipmentStatus.DELIVERED,
            "DE", ShipmentStatus.FAILED_ATTEMPT,
            "SE", ShipmentStatus.FAILED_ATTEMPT
    );

    private static final Map<String, ShipmentStatus> USPS_STATUS = Map.of(
            "Pre-Shipment", ShipmentStatus.PICKED_UP,
            "Accepted", ShipmentStatus.PICKED_UP,
            "In Transit", ShipmentStatus.IN_TRANSIT,
            "Out for Delivery", ShipmentStatus.OUT_FOR_DELIVERY,
            "Delivered", ShipmentStatus.DELIVERED
    );

    public ShipmentStatus mapDHL(String statusCode) {
        return statusCode == null ? ShipmentStatus.UNKNOWN : DHL_STATUS.getOrDefault(statusCode.toLowerCase(), ShipmentStatus.UNKNOWN);
    }

    public ShipmentStatus mapUPS(String statusType) {
        return statusType == null ? ShipmentStatus.UNKNOWN : UPS_STATUS.getOrDefault(statusType, ShipmentStatus.UNKNOWN);
    }

    public ShipmentStatus mapFedEx(String derivedStatus) {
        return derivedStatus == null ? ShipmentStatus.UNKNOWN : FEDEX_STATUS.getOrDefault(derivedStatus, ShipmentStatus.UNKNOWN);
    }

    public ShipmentStatus mapUSPS(String statusCategory) {
        return statusCategory == null ? ShipmentStatus.UNKNOWN : USPS_STATUS.getOrDefault(statusCategory, ShipmentStatus.UNKNOWN);
    }

    public ShipmentStatus mapFromDescription(String description) {
        if (description == null) return ShipmentStatus.UNKNOWN;
        String lower = description.toLowerCase();
        if (lower.contains("delivered")) return ShipmentStatus.DELIVERED;
        if (lower.contains("out for delivery")) return ShipmentStatus.OUT_FOR_DELIVERY;
        if (lower.contains("transit") || lower.contains("in transit")) return ShipmentStatus.IN_TRANSIT;
        if (lower.contains("picked up") || lower.contains("pickup") || lower.contains("collected")) return ShipmentStatus.PICKED_UP;
        if (lower.contains("returned")) return ShipmentStatus.RETURNED;
        if (lower.contains("failed") || lower.contains("exception")) return ShipmentStatus.FAILED_ATTEMPT;
        return ShipmentStatus.UNKNOWN;
    }
}
