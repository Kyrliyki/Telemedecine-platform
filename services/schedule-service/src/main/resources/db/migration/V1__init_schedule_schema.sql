

CREATE TABLE doctor_working_hours (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL,
    day_of_week INTEGER NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    buffer_minutes INTEGER NOT NULL DEFAULT 0,
    slot_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    location VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_working_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_working_time CHECK (start_time < end_time),
    CONSTRAINT chk_slot_duration CHECK (slot_duration_minutes BETWEEN 5 AND 240),
    CONSTRAINT uk_doctor_day_start_effective
        UNIQUE (doctor_id, day_of_week, start_time, effective_from)
);

CREATE INDEX idx_working_hours_doctor
    ON doctor_working_hours (doctor_id, day_of_week);

CREATE TABLE schedule_exception (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(255),
    recurrence VARCHAR(16) NOT NULL DEFAULT 'NONE',
    recurrence_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_exception_range CHECK (starts_at < ends_at)
);

CREATE INDEX idx_schedule_exception_doctor
    ON schedule_exception (doctor_id, starts_at, ends_at);

CREATE TABLE time_slot (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    appointment_id UUID,
    hold_expires_at TIMESTAMPTZ,
    slot_type VARCHAR(32) NOT NULL DEFAULT 'STANDARD',
    location VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_slot_range CHECK (starts_at < ends_at),
    CONSTRAINT uk_slot_doctor_start UNIQUE (doctor_id, starts_at),
    CONSTRAINT uk_slot_appointment UNIQUE (appointment_id)
);

CREATE INDEX idx_slot_doctor_range ON time_slot (doctor_id, starts_at, ends_at);
CREATE INDEX idx_slot_status ON time_slot (status);
CREATE INDEX idx_slot_hold_expiry ON time_slot (status, hold_expires_at);

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

CREATE INDEX idx_outbox_pending
    ON outbox_event (service, status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_event_id ON outbox_event (event_id);

CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_processed_event_processed_at
    ON processed_event (processed_at);
