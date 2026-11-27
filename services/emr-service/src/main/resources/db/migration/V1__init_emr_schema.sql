

CREATE TABLE medical_record (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    doctor_id UUID NOT NULL,
    appointment_id UUID,
    status VARCHAR(32) NOT NULL,
    chief_complaint VARCHAR(1000),
    diagnosis_code VARCHAR(32),
    diagnosis_text VARCHAR(1000),
    notes TEXT,
    treatment_plan TEXT,
    legal_hold BOOLEAN NOT NULL DEFAULT FALSE,
    retention_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    finalized_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_emr_record_appointment UNIQUE (appointment_id)
);

CREATE INDEX idx_emr_record_patient
    ON medical_record (patient_id, created_at DESC);
CREATE INDEX idx_emr_record_doctor
    ON medical_record (doctor_id, created_at DESC);
CREATE INDEX idx_emr_record_status ON medical_record (status);
CREATE INDEX idx_emr_record_retention
    ON medical_record (retention_until)
    WHERE legal_hold = FALSE AND deleted_at IS NULL;

CREATE TABLE prescription (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record (id) ON DELETE CASCADE,
    medication_name VARCHAR(255) NOT NULL,
    dosage VARCHAR(120) NOT NULL,
    frequency VARCHAR(120) NOT NULL,
    duration_days INTEGER NOT NULL,
    instructions VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_prescription_duration CHECK (duration_days BETWEEN 1 AND 365)
);

CREATE INDEX idx_prescription_record ON prescription (record_id);

CREATE TABLE vital_signs (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL UNIQUE REFERENCES medical_record (id) ON DELETE CASCADE,
    height_cm NUMERIC(5, 2),
    weight_kg NUMERIC(5, 2),
    systolic INTEGER,
    diastolic INTEGER,
    pulse INTEGER,
    temperature_c NUMERIC(4, 2),
    spo2 INTEGER,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_vitals_systolic CHECK (systolic IS NULL OR systolic BETWEEN 40 AND 300),
    CONSTRAINT chk_vitals_diastolic CHECK (diastolic IS NULL OR diastolic BETWEEN 20 AND 200),
    CONSTRAINT chk_vitals_pulse CHECK (pulse IS NULL OR pulse BETWEEN 20 AND 300),
    CONSTRAINT chk_vitals_spo2 CHECK (spo2 IS NULL OR spo2 BETWEEN 50 AND 100)
);

CREATE TABLE medical_attachment (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record (id) ON DELETE CASCADE,
    file_id UUID NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_emr_attachment_file UNIQUE (record_id, file_id)
);

CREATE INDEX idx_emr_attachment_record ON medical_attachment (record_id);

CREATE TABLE medical_record_revision (
    id UUID PRIMARY KEY,
    record_id UUID NOT NULL REFERENCES medical_record (id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL,
    snapshot TEXT NOT NULL,
    changed_by UUID,
    change_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_record_revision UNIQUE (record_id, revision_number)
);

CREATE INDEX idx_record_revision_record
    ON medical_record_revision (record_id, revision_number DESC);

CREATE TABLE emr_audit_log (
    id UUID PRIMARY KEY,
    record_id UUID,
    actor_id UUID,
    action VARCHAR(64) NOT NULL,
    ip_address VARCHAR(64),
    request_id VARCHAR(128),
    correlation_id VARCHAR(128),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_emr_audit_record ON emr_audit_log (record_id, created_at DESC);
CREATE INDEX idx_emr_audit_actor ON emr_audit_log (actor_id, created_at DESC);
CREATE INDEX idx_emr_audit_created ON emr_audit_log (created_at);

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
