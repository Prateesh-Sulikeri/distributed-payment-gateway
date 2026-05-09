CREATE TABLE payments (
                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          idempotency_key VARCHAR(255) NOT NULL UNIQUE,
                          amount DECIMAL(19, 4) NOT NULL,
                          currency VARCHAR(3) NOT NULL DEFAULT 'INR',
                          status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                          payment_method VARCHAR(20) NOT NULL,
                          description VARCHAR(500),
                          failure_reason VARCHAR(500),
                          created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                          updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);