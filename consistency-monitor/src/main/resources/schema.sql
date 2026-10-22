CREATE TABLE IF NOT EXISTS invariant_violations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inv_id VARCHAR(32) NOT NULL,
    entity_key VARCHAR(191) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    first_seen DATETIME(6) NOT NULL,
    last_seen DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    acked_by VARCHAR(64) NULL,
    resolution_note VARCHAR(500) NULL,
    snapshot_json TEXT NOT NULL
);
