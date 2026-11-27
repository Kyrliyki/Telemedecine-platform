package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.domain.model.*;
import com.telemed.auth.domain.valueobject.*;
import com.telemed.auth.infrastructure.persistence.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserIdentityEntityMapper {

  private final LocalCredentialEntityMapper credentialMapper;

  public UserIdentity toDomain(UserIdentityEntity e) {
    if (e instanceof LocalIdentityEntity local) {
      return new LocalIdentity(
          new UserIdentityId(local.getId()),
          new Email(local.getProviderSubject()),
          new ProviderSubject(local.getProviderSubject()),
          credentialMapper.toDomain(local.getCredential()),
          local.getLinkedAt());
    }
    if (e instanceof ExternalIdentityEntity external) {
      return new ExternalIdentity(
          new UserIdentityId(external.getId()),
          external.getProvider(),
          new ProviderSubject(external.getProviderSubject()),
          new Email(external.getProviderEmail()),
          external.getEmailVerified(),
          external.getLinkedAt());
    }
    throw new IllegalStateException("Unknown identity type: " + e.getClass());
  }

  public UserIdentityEntity toEntity(UserIdentity identity, AuthUserEntity parent) {
    UserIdentityEntity e;
    if (identity instanceof LocalIdentity local) {
      var localEntity = new LocalIdentityEntity();
      localEntity.setId(local.id().id());
      localEntity.setCredential(credentialMapper.toEntity(local.credential(), localEntity));
      e = localEntity;
    } else if (identity instanceof ExternalIdentity external) {
      var externalEntity = new ExternalIdentityEntity();
      externalEntity.setProviderEmail(external.email().value());
      externalEntity.setEmailVerified(external.emailVerified());
      e = externalEntity;
    } else {
      throw new IllegalStateException("Unknown identity: " + identity.getClass());
    }
    e.setId(identity.id().id());
    e.setUser(parent);
    e.setProvider(identity.provider());
    e.setProviderSubject(identity.subject().value());
    e.setLinkedAt(identity.linkedAt());
    return e;
  }
}
