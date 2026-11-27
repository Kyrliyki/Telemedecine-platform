

CREATE TABLE auth_users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    email_verified BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE auth_user_roles (
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL,
    identity_type VARCHAR(31) NOT NULL,
    provider_email VARCHAR(255),
    email_verified BOOLEAN,
    CONSTRAINT uk_user_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_user_identity_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE local_credentials (
    identity_id UUID PRIMARY KEY REFERENCES user_identities (id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted BOOLEAN NOT NULL,
    accepted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_invitation_hash ON invitations (token_hash);
CREATE INDEX idx_invitation_user ON invitations (user_id);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL,
    revoked_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_token_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_token_user ON refresh_tokens (user_id);

CREATE TABLE oauth_link_confirmation_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    provider_subject VARCHAR(255),
    provider_email VARCHAR(320),
    provider_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_oauth_link_confirmation_user
        FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth_link_hash ON oauth_link_confirmation_tokens (token_hash);
CREATE INDEX idx_oauth_link_user ON oauth_link_confirmation_tokens (user_id);

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id) REFERENCES auth_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_hash ON email_verification_tokens (token_hash);
CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);

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

CREATE TABLE auth_audit_log (
    id UUID PRIMARY KEY,
    actor_id UUID,
    target_user_id UUID,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    request_id VARCHAR(128),
    correlation_id VARCHAR(128),
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_audit_created_at ON auth_audit_log (created_at);
CREATE INDEX idx_auth_audit_target ON auth_audit_log (target_user_id, created_at);
CREATE INDEX idx_auth_audit_action ON auth_audit_log (action, created_at);
