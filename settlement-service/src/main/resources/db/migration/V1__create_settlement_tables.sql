CREATE TABLE settlements
(
    id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    total_amount NUMERIC(19,4) NOT NULL,

    payment_count INTEGER NOT NULL,

    period_date DATE NOT NULL,

    status VARCHAR(50) NOT NULL,

    created_at TIMESTAMP NOT NULL,

    CONSTRAINT uk_settlement_merchant_period
        UNIQUE (merchant_id, period_date)
);

CREATE TABLE settlement_payment_records
(
    payment_id UUID PRIMARY KEY,

    merchant_id UUID NOT NULL,

    amount NUMERIC(19,4) NOT NULL,

    currency VARCHAR(3) NOT NULL,

    payment_created_at TIMESTAMP NOT NULL,

    settled BOOLEAN NOT NULL DEFAULT FALSE,

    settlement_id UUID NULL,

    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_spr_merchant
    ON settlement_payment_records (merchant_id);

CREATE INDEX idx_spr_settled
    ON settlement_payment_records (settled);

CREATE INDEX idx_spr_merchant_settled
    ON settlement_payment_records (merchant_id, settled);