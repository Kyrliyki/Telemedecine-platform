package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;

public interface OAuthLinkConfirmationTokenGenerator {

  Generated generate(
      UserId userId,
      ProviderType provider,
      String providerSubject,
      String providerEmail,
      boolean providerEmailVerified,
      Instant now);

  String hash(String rawToken);

  record Generated(String rawToken, OAuthLinkConfirmationTokenRepository.Stored stored) {}
}
