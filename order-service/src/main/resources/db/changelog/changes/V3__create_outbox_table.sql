CREATE TABLE outbox_event (
   id BIGSERIAL PRIMARY KEY,
   aggregatetype VARCHAR(255) NOT NULL,      -- 'Order'
   aggregateid VARCHAR(255) NOT NULL,        -- orderId
   eventtype VARCHAR(255) NOT NULL,          -- 'OrderCreatedEvent'
   payload JSON NOT NULL,                    -- тело события
   status VARCHAR(50) NOT NULL DEFAULT 'NEW', -- NEW, PUBLISHED
   trace_id VARCHAR(32) NOT NULL,
   span_id VARCHAR(16) NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
   processed_at TIMESTAMP WITH TIME ZONE,
   traceparent VARCHAR(255)
);

CREATE INDEX idx_outbox_event_status_created_at ON outbox_event (status, created_at);