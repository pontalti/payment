DROP TABLE IF EXISTS payment;

CREATE TABLE payment (
    id            BIGSERIAL PRIMARY KEY,
    uuid          UUID           NOT NULL UNIQUE,
    order_id      VARCHAR(255)   NOT NULL,
    amount        NUMERIC(19, 2) NOT NULL,
    currency      VARCHAR(3)     NOT NULL,
    method        VARCHAR(20)    NOT NULL,
    funding_type  VARCHAR(20),
    status        VARCHAR(20)    NOT NULL,
    processed_at  TIMESTAMP      NOT NULL
);