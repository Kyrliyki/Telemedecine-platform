package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.persistence.entity.AuthUserEntity;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUserEntityMapper {

  private final UserIdentityEntityMapper identityMapper;

  public AuthUser toDomain(AuthUserEntity e) {
    return AuthUser.rehydrate(
        new UserId(e.getId()),
        new Email(e.getEmail()),
        e.isEmailVerified(),
        e.getStatus(),
        e.getRoles(),
        e.getIdentities().stream().map(identityMapper::toDomain).collect(Collectors.toSet()),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }

  public AuthUserEntity toEntity(AuthUser user) {
    AuthUserEntity e = new AuthUserEntity();
    e.setId(user.id().value());
    e.setEmail(user.email().value());
    e.setEmailVerified(user.emailVerified());
    e.setStatus(user.status());
    e.setRoles(new HashSet<>(user.roles()));
    e.setIdentities(
        user.identities().stream()
            .map(i -> identityMapper.toEntity(i, e))
            .collect(Collectors.toSet()));
    e.setCreatedAt(user.createdAt());
    e.setUpdatedAt(user.updatedAt());
    return e;
  }
}
