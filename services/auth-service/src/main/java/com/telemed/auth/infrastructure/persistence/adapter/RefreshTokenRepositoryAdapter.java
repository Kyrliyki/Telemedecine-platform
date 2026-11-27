package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.RefreshTokenRepository;
import com.telemed.auth.domain.model.RefreshToken;
import com.telemed.auth.domain.valueobject.RefreshTokenId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.infrastructure.persistence.mapper.RefreshTokenEntityMapper;
import com.telemed.auth.infrastructure.persistence.repository.JpaRefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

  private final JpaRefreshTokenRepository jpa;
  private final RefreshTokenEntityMapper mapper;

  @Override
  public RefreshToken save(RefreshToken token) {
    var entity = mapper.toEntity(token);
    jpa.findById(entity.getId()).ifPresent(existing -> entity.setVersion(existing.getVersion()));
    return mapper.toDomain(jpa.save(entity));
  }

  @Override
  public Optional<RefreshToken> findByTokenHash(TokenHash hash) {
    return jpa.findByTokenHashForUpdate(hash.value()).map(mapper::toDomain);
  }

  @Override
  public Optional<RefreshToken> findById(RefreshTokenId id) {
    return jpa.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public void revokeFamily(UUID familyId) {
    jpa.revokeByFamilyId(familyId, Instant.now());
  }

  @Override
  public int revokeAllByUserId(UUID userId, Instant now) {
    return jpa.revokeAllByUserId(userId, now);
  }
}
