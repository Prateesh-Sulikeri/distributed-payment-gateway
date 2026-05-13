CREATE TABLE outbox_events (
                               event_id UUID PRIMARY KEY,
                               event_type VARCHAR(50) NOT NULL,
                               payload TEXT NOT NULL,
                               status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                               created_at TIMESTAMP NOT NULL,
                               updated_at TIMESTAMP
);

CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);