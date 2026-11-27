package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.ProviderSubject;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserIdentityId;
import java.time.Instant;

public sealed interface UserIdentity permits ExternalIdentity, LocalIdentity {
  UserIdentityId id();

  ProviderSubject subject();

  ProviderType provider();

  Instant linkedAt();
}
