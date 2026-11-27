package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.EmailVerificationTokenRepository;
import com.telemed.auth.application.port.output.TokenHasher;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.persistence.entity.EmailVerificationTokenEntity;
import com.telemed.auth.infrastructure.persistence.repository.JpaEmailVerificationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationTokenRepositoryAdapter implements EmailVerificationTokenRepository {

  private final JpaEmailVerificationTokenRepository jpa;
  private final TokenHasher tokenHasher;

  @Override
  public Stored save(Stored token) {
    EmailVerificationTokenEntity entity = new EmailVerificationTokenEntity();
    entity.setId(token.id() == null ? UUID.randomUUID() : token.id());
    entity.setUserId(token.userId().value());
    entity.setTokenHash(token.tokenHash());
    entity.setExpiresAt(token.expiresAt());
    entity.setUsed(token.used());
    entity.setCreatedAt(Instant.now());
    EmailVerificationTokenEntity saved = jpa.save(entity);
    return toStored(saved);
  }

  @Override
  public Optional<Stored> findUsable(String rawToken, Instant now) {
    String hash = tokenHasher.hash(rawToken).value();
    return jpa.findByTokenHashAndUsedFalseAndExpiresAtAfter(hash, now).map(this::toStored);
  }

  @Override
  public void markUsed(UUID id, Instant now) {
    jpa.markUsed(id, now);
  }

  private Stored toStored(EmailVerificationTokenEntity entity) {
    return new Stored(
        entity.getId(),
        new UserId(entity.getUserId()),
        entity.getTokenHash(),
        entity.getExpiresAt(),
        entity.isUsed());
  }
}
