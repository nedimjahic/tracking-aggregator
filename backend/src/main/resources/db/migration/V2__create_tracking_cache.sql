CREATE TABLE tracking_cache (
    id               BIGSERIAL PRIMARY KEY,
    tracking_number  VARCHAR(50)  NOT NULL,
    courier          VARCHAR(20)  NOT NULL,
    response_json    TEXT         NOT NULL,
    cached_at        TIMESTAMP    NOT NULL,
    shipment_status  VARCHAR(30),
    UNIQUE (tracking_number, courier)
);

CREATE INDEX idx_tracking_cache_lookup ON tracking_cache (tracking_number, courier);
