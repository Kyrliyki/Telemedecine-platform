package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.port.output.EmailVerificationTokenGenerator;
import com.telemed.auth.application.port.output.EmailVerificationTokenRepository;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.config.AuthProperties;
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
public class Sha256EmailVerificationTokenGenerator implements EmailVerificationTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;
  private static final Duration DEFAULT_TTL = Duration.ofHours(24);

  private final AuthProperties properties;

  public Sha256EmailVerificationTokenGenerator(AuthProperties properties) {
    this.properties = properties;
  }

  @Override
  public Generated generate(UserId userId, Instant now) {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Duration ttl =
        properties.emailVerificationTtl() != null ? properties.emailVerificationTtl() : DEFAULT_TTL;
    var stored =
        new EmailVerificationTokenRepository.Stored(
            UUID.randomUUID(), userId, sha256(raw), now.plus(ttl), false);
    return new Generated(raw, stored);
  }

  @Override
  public EmailVerificationTokenRepository.Stored hash(String rawToken, Instant now) {
    return new EmailVerificationTokenRepository.Stored(null, null, sha256(rawToken), now, false);
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
