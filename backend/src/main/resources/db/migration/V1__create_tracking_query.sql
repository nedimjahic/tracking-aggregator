CREATE TABLE tracking_query (
    id             BIGSERIAL PRIMARY KEY,
    tracking_number VARCHAR(50)  NOT NULL,
    courier         VARCHAR(20),
    status          VARCHAR(30),
    queried_at      TIMESTAMP    NOT NULL
);
