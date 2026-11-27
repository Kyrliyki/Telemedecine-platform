package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.domain.valueobject.LocalCredentials;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.infrastructure.persistence.entity.LocalCredentialEntity;
import com.telemed.auth.infrastructure.persistence.entity.LocalIdentityEntity;
import org.springframework.stereotype.Component;

@Component
public class LocalCredentialEntityMapper {

  public LocalCredentials toDomain(LocalCredentialEntity e) {
    return new LocalCredentials(new PasswordHash(e.getPasswordHash()), e.getUpdatedAt());
  }

  public LocalCredentialEntity toEntity(LocalCredentials credential, LocalIdentityEntity identity) {
    LocalCredentialEntity e = new LocalCredentialEntity();
    e.setIdentityId(identity.getId());
    e.setIdentity(identity);
    e.setPasswordHash(credential.passwordHash().passwordHash());
    e.setUpdatedAt(credential.updatedAt());
    return e;
  }
}
