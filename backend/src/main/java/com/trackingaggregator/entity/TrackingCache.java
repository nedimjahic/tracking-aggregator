package com.trackingaggregator.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "tracking_cache",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tracking_number", "courier"}))
public class TrackingCache extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tracking_number", nullable = false, length = 50)
    public String trackingNumber;

    @Column(length = 20, nullable = false)
    public String courier;

    @Column(name = "response_json", columnDefinition = "TEXT", nullable = false)
    public String responseJson;

    @Column(name = "cached_at", nullable = false)
    public LocalDateTime cachedAt;

    @Column(name = "shipment_status", length = 30)
    public String shipmentStatus;

    public static TrackingCache findByTrackingNumberAndCourier(String trackingNumber, String courier) {
        return find("trackingNumber = ?1 and courier = ?2", trackingNumber, courier).firstResult();
    }
}
