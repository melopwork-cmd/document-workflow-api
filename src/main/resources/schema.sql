CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    state VARCHAR(20) NOT NULL,
    version INTEGER NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS document_events (
    document_id VARCHAR(36) NOT NULL REFERENCES documents(id),
    version INTEGER NOT NULL,
    from_state VARCHAR(20),
    to_state VARCHAR(20) NOT NULL,
    occurred_at BIGINT NOT NULL,
    PRIMARY KEY (document_id, version)
);
