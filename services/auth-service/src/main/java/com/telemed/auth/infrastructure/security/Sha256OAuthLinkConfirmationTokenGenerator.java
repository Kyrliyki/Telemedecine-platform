package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenGenerator;
import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenRepository;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class Sha256OAuthLinkConfirmationTokenGenerator
    implements OAuthLinkConfirmationTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;
  private static final Duration TTL = Duration.ofHours(1);

  @Override
  public Generated generate(
      UserId userId,
      ProviderType provider,
      String providerSubject,
      String providerEmail,
      boolean providerEmailVerified,
      Instant now) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var stored =
        new OAuthLinkConfirmationTokenRepository.Stored(
            UUID.randomUUID(),
            userId,
            provider,
            sha256(raw),
            now.plus(TTL),
            false,
            providerSubject,
            providerEmail,
            providerEmailVerified);
    return new Generated(raw, stored);
  }

  @Override
  public String hash(String rawToken) {
    return sha256(rawToken);
  }

  private String sha256(String input) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 not available", ex);
    }
  }
}
