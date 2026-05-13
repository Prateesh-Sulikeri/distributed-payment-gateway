CREATE TABLE distributed_locks (
                                   lock_name VARCHAR(255) PRIMARY KEY,
                                   locked_by VARCHAR(255) NOT NULL,
                                   locked_at TIMESTAMP NOT NULL,
                                   expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_locks_expires_at ON distributed_locks(expires_at);