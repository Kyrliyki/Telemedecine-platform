package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.ProviderSubject;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserIdentityId;
import java.time.Instant;
import java.util.Objects;

public record ExternalIdentity(
    UserIdentityId id,
    ProviderType provider,
    ProviderSubject subject,
    Email email,
    boolean emailVerified,
    Instant linkedAt)
    implements UserIdentity {
  public ExternalIdentity {
    Objects.requireNonNull(linkedAt);
    if (provider == ProviderType.LOCAL) {
      throw new IllegalArgumentException("ExternalIdentity cannot be LOCAL");
    }
  }

  public static ExternalIdentity link(
      ProviderType provider,
      ProviderSubject subject,
      Email email,
      boolean emailVerified,
      Instant now) {
    Objects.requireNonNull(now);
    return new ExternalIdentity(
        UserIdentityId.newId(), provider, subject, email, emailVerified, now);
  }
}
