package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.view.IntrospectionResult;
import com.telemed.auth.application.port.input.TokenIntrospectionUseCase;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.common.security.blacklist.TokenBlacklist;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenIntrospectionService implements TokenIntrospectionUseCase {
  private final JwtDecoder jwtDecoder;
  private final TokenBlacklist tokenBlacklist;
  private final Clock clock;

  @Override
  public IntrospectionResult introspect(String accessToken) {
    try {
      Jwt jwt = jwtDecoder.decode(accessToken);
      String jti = jwt.getId();
      Instant exp = jwt.getExpiresAt();
      if (jti == null || tokenBlacklist.isBlacklisted(jti)) {
        return new IntrospectionResult(false, null, Set.of(), exp, "blacklisted");
      }
      if (exp == null || exp.isBefore(clock.now())) {
        return new IntrospectionResult(false, null, Set.of(), exp, "expired");
      }
      String subject = jwt.getSubject();
      if (subject == null) {
        return new IntrospectionResult(false, null, Set.of(), exp, "missing subject");
      }
      var roles = jwt.getClaimAsStringList("roles");
      return new IntrospectionResult(
          true,
          java.util.UUID.fromString(subject),
          roles == null ? Set.of() : Set.copyOf(roles),
          exp,
          null);

    } catch (Exception ex) {
      return new IntrospectionResult(false, null, Set.of(), null, "invalid");
    }
  }
}
