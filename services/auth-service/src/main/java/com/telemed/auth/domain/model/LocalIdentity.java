package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.*;
import java.time.Instant;
import java.util.Objects;

public record LocalIdentity(
    UserIdentityId id,
    Email email,
    ProviderSubject subject,
    LocalCredentials credential,
    Instant linkedAt)
    implements UserIdentity {

  public LocalIdentity {
    Objects.requireNonNull(linkedAt);
  }

  public static LocalIdentity create(Email email, PasswordHash hash, Instant now) {
    Objects.requireNonNull(now);
    return new LocalIdentity(
        UserIdentityId.newId(),
        email,
        new ProviderSubject(email.value()),
        LocalCredentials.of(hash, now),
        now);
  }

  @Override
  public ProviderType provider() {
    return ProviderType.LOCAL;
  }
}
