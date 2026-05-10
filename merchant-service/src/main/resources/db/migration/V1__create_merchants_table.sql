CREATE TABLE merchants (
                           id UUID PRIMARY KEY,
                           name VARCHAR(255) NOT NULL,
                           email VARCHAR(255) NOT NULL UNIQUE,
                           api_key_hash VARCHAR(255) NOT NULL UNIQUE,
                           webhook_url VARCHAR(255),
                           status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                           created_at TIMESTAMP NOT NULL,
                           updated_at TIMESTAMP
);

CREATE INDEX idx_merchants_email ON merchants(email);
CREATE INDEX idx_merchants_api_key_hash ON merchants(api_key_hash);