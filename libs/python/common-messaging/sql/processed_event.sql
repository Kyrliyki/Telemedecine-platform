CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_event_processed_at
    ON processed_event (processed_at);
