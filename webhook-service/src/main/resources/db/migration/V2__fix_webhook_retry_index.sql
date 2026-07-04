-- The original idx_webhook_retry partial index only covered status = 'PENDING',
-- but WebhookRetryJob queries status IN ('PENDING', 'FAILED') ordered/filtered by next_retry_at.
-- Recreate the partial index to cover both statuses actually queried.
DROP INDEX IF EXISTS idx_webhook_retry;

CREATE INDEX idx_webhook_retry ON webhook_deliveries (next_retry_at) WHERE status IN ('PENDING', 'FAILED');
