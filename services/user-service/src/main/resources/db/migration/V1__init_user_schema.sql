

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    auth_updated_at TIMESTAMPTZ
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE doctor_profiles (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    specialization VARCHAR(120) NOT NULL,
    bio TEXT NOT NULL DEFAULT '',
    years_experience INTEGER NOT NULL DEFAULT 0,
    license_number VARCHAR(64) NOT NULL DEFAULT '',
    accepting_patients BOOLEAN NOT NULL DEFAULT TRUE,
    verification_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    rejection_reason VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_doctor_experience CHECK (years_experience >= 0 AND years_experience <= 80)
);

CREATE INDEX idx_doctor_profiles_specialization
    ON doctor_profiles (LOWER(specialization));
CREATE INDEX idx_doctor_profiles_verification
    ON doctor_profiles (verification_status);

CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_processed_event_processed_at
    ON processed_event (processed_at);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(255) NOT NULL,
    service VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    processing_started_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 1
);

CREATE INDEX idx_outbox_pending ON outbox_event (service, status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_event_id ON outbox_event (event_id);
