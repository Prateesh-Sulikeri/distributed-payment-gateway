ALTER TABLE payments ADD COLUMN merchant_id VARCHAR(255) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';
CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);