package com.telemed.common.security.util;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.common.security.principal.AuthenticatedUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityContextUtils {

  private SecurityContextUtils() {}

  public static Optional<Jwt> currentJwt() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      return Optional.empty();
    }
    return Optional.of(jwt);
  }

  public static Optional<UUID> currentUserId() {
    return currentJwt().flatMap(JwtClaimsExtractor::userId);
  }

  public static Optional<AuthenticatedUser> currentUser() {
    return currentJwt()
        .flatMap(
            jwt -> {
              Optional<UUID> userId = JwtClaimsExtractor.userId(jwt);
              if (userId.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(
                  new AuthenticatedUser(
                      userId.get(),
                      JwtClaimsExtractor.email(jwt).orElse(null),
                      Set.copyOf(JwtClaimsExtractor.roles(jwt)),
                      JwtClaimsExtractor.jti(jwt).orElse(null),
                      JwtClaimsExtractor.expiresAt(jwt).orElse(null)));
            });
  }

  public static boolean isAuthenticated() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth != null && auth.isAuthenticated();
  }
}
