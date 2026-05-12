CREATE TABLE webhook_deliveries (
                                    id UUID PRIMARY KEY,
                                    merchant_id UUID NOT NULL,
                                    payment_id UUID NOT NULL,
                                    webhook_url VARCHAR(500) NOT NULL,
                                    payload TEXT NOT NULL,
                                    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
                                    attempt_count INT NOT NULL DEFAULT 0,
                                    next_retry_at TIMESTAMP,
                                    last_error_message VARCHAR(1000),
                                    created_at TIMESTAMP NOT NULL,
                                    updated_at TIMESTAMP,
                                    delivered_at TIMESTAMP
);

CREATE INDEX idx_webhook_status ON webhook_deliveries(status);
CREATE INDEX idx_webhook_retry ON webhook_deliveries(next_retry_at) WHERE status = 'PENDING';
CREATE INDEX idx_webhook_merchant ON webhook_deliveries(merchant_id);