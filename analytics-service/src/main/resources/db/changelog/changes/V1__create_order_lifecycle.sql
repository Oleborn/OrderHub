CREATE TABLE IF NOT EXISTS order_lifecycle
(
    order_id             BIGINT PRIMARY KEY,
    created_at           TIMESTAMPTZ,
    payment_started_at   TIMESTAMPTZ,
    payment_completed_at TIMESTAMPTZ,
    payment_status       VARCHAR(25),
    notification_sent_at TIMESTAMPTZ
);

CREATE INDEX idx_order_lifecycle_created_at ON order_lifecycle (created_at);
CREATE INDEX idx_order_lifecycle_payment_completed_at ON order_lifecycle (payment_completed_at);