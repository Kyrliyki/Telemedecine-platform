package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenRepository;
import com.telemed.auth.application.port.output.TokenHasher;
import com.telemed.auth.infrastructure.persistence.mapper.OAuthLinkConfirmationTokenEntityMapper;
import com.telemed.auth.infrastructure.persistence.repository.JpaOAuthLinkConfirmationTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OAuthLinkConfirmationTokenRepositoryAdapter
    implements OAuthLinkConfirmationTokenRepository {

  private final JpaOAuthLinkConfirmationTokenRepository jpa;
  private final OAuthLinkConfirmationTokenEntityMapper mapper;
  private final TokenHasher tokenHasher;

  @Override
  public Stored save(Stored token) {
    return mapper.toStored(jpa.save(mapper.toEntity(token)));
  }

  @Override
  public Optional<Stored> findUsable(String rawToken, Instant now) {
    String hash = tokenHasher.hash(rawToken).value();
    return jpa.findByTokenHashAndUsedFalseAndExpiresAtAfter(hash, now).map(mapper::toStored);
  }

  @Override
  public void markUsed(UUID id, Instant now) {
    jpa.markUsed(id, now);
  }
}
