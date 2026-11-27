package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record OAuthLinkConfirmationRequestedEvent(
    UUID eventId,
    Instant occurredAt,
    UserId userId,
    Email email,
    ProviderType provider,
    Instant expiresAt)
    implements DomainEvent {}
