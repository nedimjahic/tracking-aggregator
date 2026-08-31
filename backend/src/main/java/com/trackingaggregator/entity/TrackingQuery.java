package com.trackingaggregator.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "tracking_query")
public class TrackingQuery extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "tracking_number", nullable = false, length = 50)
    public String trackingNumber;

    @Column(length = 20)
    public String courier;

    @Column(length = 30)
    public String status;

    @Column(name = "queried_at", nullable = false)
    public LocalDateTime queriedAt;
}
