package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.domain.model.RefreshToken;
import com.telemed.auth.domain.valueobject.RefreshTokenId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenEntityMapper {

  public RefreshToken toDomain(RefreshTokenEntity e) {
    return RefreshToken.rehydrate(
        new RefreshTokenId(e.getId()),
        new UserId(e.getUserId()),
        new TokenHash(e.getTokenHash()),
        e.getFamilyId(),
        e.getIssuedAt(),
        e.getExpiresAt(),
        e.isRevoked(),
        e.getRevokedAt());
  }

  public RefreshTokenEntity toEntity(RefreshToken t) {
    RefreshTokenEntity e = new RefreshTokenEntity();
    e.setId(t.id().value());
    e.setUserId(t.userId().value());
    e.setTokenHash(t.tokenHash().value());
    e.setFamilyId(t.familyId());
    e.setIssuedAt(t.issuedAt());
    e.setExpiresAt(t.expiresAt());
    e.setRevoked(t.revoked());
    e.setRevokedAt(t.revokedAt());
    return e;
  }
}
