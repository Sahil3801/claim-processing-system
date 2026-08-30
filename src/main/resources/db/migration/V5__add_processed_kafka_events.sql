CREATE TABLE processed_kafka_events (
    event_id VARCHAR(36) PRIMARY KEY,
    claim_id BIGINT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_processed_kafka_events_processed_at
    ON processed_kafka_events (processed_at);
