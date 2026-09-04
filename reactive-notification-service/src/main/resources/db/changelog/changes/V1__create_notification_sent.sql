CREATE TABLE IF NOT EXISTS notification_sent
(
    id       BIGSERIAL PRIMARY KEY,
    order_id BIGINT      NOT NULL,
    status   VARCHAR(25) NOT NULL,
    sent_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_notification_sent_order_id ON notification_sent (order_id);