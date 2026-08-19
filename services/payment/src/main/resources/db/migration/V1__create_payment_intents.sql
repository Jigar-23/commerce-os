-- Versioned schema for the payment service. Owned by Flyway; Hibernate validate only.
CREATE TABLE payment_intents (
    id                  UUID PRIMARY KEY,
    order_id            UUID NOT NULL UNIQUE,
    customer_id         UUID NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'INR',
    payment_method      VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    captured_at         TIMESTAMP,
    refunded_at         TIMESTAMP,
    gateway_event_id    VARCHAR(128),
    webhook_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_payment_order UNIQUE (order_id)
);

CREATE INDEX idx_payment_customer ON payment_intents (customer_id);
CREATE INDEX idx_payment_status ON payment_intents (status);