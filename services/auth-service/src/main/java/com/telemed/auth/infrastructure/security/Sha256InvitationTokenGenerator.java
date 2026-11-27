package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.port.output.InvitationTokenGenerator;
import com.telemed.auth.domain.valueobject.TokenHash;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class Sha256InvitationTokenGenerator implements InvitationTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  @Override
  public Generated generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new Generated(raw, hash(raw));
  }

  @Override
  public TokenHash hash(String rawToken) {
    return new TokenHash(sha256(rawToken));
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
