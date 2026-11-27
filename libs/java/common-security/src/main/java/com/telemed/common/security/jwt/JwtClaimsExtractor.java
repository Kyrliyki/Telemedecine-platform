package com.telemed.common.security.jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtClaimsExtractor {

  private JwtClaimsExtractor() {}

  public static Optional<UUID> userId(Jwt jwt) {
    String sub = jwt.getSubject();
    if (sub == null || sub.isBlank()) return Optional.empty();
    try {
      return Optional.of(UUID.fromString(sub));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  public static Optional<String> jti(Jwt jwt) {
    String jti = jwt.getId();
    return (jti == null || jti.isBlank()) ? Optional.empty() : Optional.of(jti);
  }

  public static Optional<Instant> expiresAt(Jwt jwt) {
    return Optional.ofNullable(jwt.getExpiresAt());
  }

  public static List<String> roles(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    return roles == null ? List.of() : List.copyOf(roles);
  }

  public static Optional<String> email(Jwt jwt) {
    String email = jwt.getClaimAsString("email");
    return (email == null || email.isBlank()) ? Optional.empty() : Optional.of(email);
  }

  public static boolean emailVerified(Jwt jwt) {
    Object value = jwt.getClaim("email_verified");
    if (value instanceof Boolean verified) {
      return verified;
    }
    if (value instanceof String text) {
      return Boolean.parseBoolean(text);
    }
    return false;
  }
}
