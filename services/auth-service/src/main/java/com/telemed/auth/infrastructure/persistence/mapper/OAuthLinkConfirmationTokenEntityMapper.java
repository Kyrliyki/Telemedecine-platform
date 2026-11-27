package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenRepository.Stored;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.persistence.entity.OAuthLinkConfirmationTokenEntity;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OAuthLinkConfirmationTokenEntityMapper {

  public Stored toStored(OAuthLinkConfirmationTokenEntity e) {
    return new Stored(
        e.getId(),
        new UserId(e.getUserId()),
        e.getProvider(),
        e.getTokenHash(),
        e.getExpiresAt(),
        e.isUsed(),
        e.getProviderSubject(),
        e.getProviderEmail(),
        e.isProviderEmailVerified());
  }

  public OAuthLinkConfirmationTokenEntity toEntity(Stored s) {
    OAuthLinkConfirmationTokenEntity e = new OAuthLinkConfirmationTokenEntity();
    e.setId(s.id() == null ? UUID.randomUUID() : s.id());
    e.setUserId(s.userId().value());
    e.setProvider(s.provider());
    e.setTokenHash(s.tokenHash());
    e.setExpiresAt(s.expiresAt());
    e.setUsed(s.used());
    e.setProviderSubject(s.providerSubject());
    e.setProviderEmail(s.providerEmail());
    e.setProviderEmailVerified(s.providerEmailVerified());
    e.setCreatedAt(java.time.Instant.now());
    return e;
  }
}
