package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.dto.view.*;
import com.telemed.auth.application.policy.RefreshTokenPolicy;
import com.telemed.auth.application.port.output.*;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.RefreshToken;
import com.telemed.common.security.jwt.JwtProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class NimbusJwtTokenIssuer implements TokenIssuer {

  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;
  private final RefreshTokenPolicy policy;
  private final TokenHasher tokenHasher;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  public NimbusJwtTokenIssuer(
      JwtEncoder jwtEncoder,
      JwtDecoder jwtDecoder,
      RefreshTokenPolicy policy,
      TokenHasher tokenHasher,
      RefreshTokenRepository refreshTokenRepository,
      JwtProperties jwtProperties,
      Clock clock) {
    this.jwtEncoder = jwtEncoder;
    this.jwtDecoder = jwtDecoder;
    this.policy = policy;
    this.tokenHasher = tokenHasher;
    this.refreshTokenRepository = refreshTokenRepository;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
  }

  @Override
  public String issueAccessToken(AuthUser user) {
    Instant now = clock.now();
    Instant exp = now.plus(policy.accessTtl());
    String jti = UUID.randomUUID().toString();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .subject(user.id().value().toString())
            .audience(jwtProperties.audience())
            .issuedAt(now)
            .expiresAt(exp)
            .id(jti)
            .claim("roles", user.roles().stream().map(Enum::name).toList())
            .claim("email", user.email().value())
            .claim("email_verified", user.emailVerified())
            .build();

    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).type("JWT").build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  @Override
  public TokenPairResult issue(AuthUser user) {
    Instant now = clock.now();
    String access = issueAccessToken(user);
    var newRefresh = tokenHasher.hashNew();
    RefreshToken refreshToken =
        RefreshToken.issue(user.id(), newRefresh.hash(), now, policy.refreshTtl());
    refreshTokenRepository.save(refreshToken);
    return new TokenPairResult(
        access, newRefresh.rawToken(), now.plus(policy.accessTtl()), refreshToken.expiresAt());
  }

  @Override
  public IntrospectionResult introspect(String accessToken) {
    try {
      Jwt jwt = jwtDecoder.decode(accessToken);
      String subject = jwt.getSubject();
      if (subject == null) {
        return new IntrospectionResult(
            false, null, java.util.Set.of(), jwt.getExpiresAt(), "missing subject");
      }
      var roles = jwt.getClaimAsStringList("roles");
      return new IntrospectionResult(
          true,
          UUID.fromString(subject),
          roles == null ? java.util.Set.of() : java.util.Set.copyOf(roles),
          jwt.getExpiresAt(),
          null);
    } catch (JwtException ex) {
      return new IntrospectionResult(false, null, java.util.Set.of(), null, ex.getMessage());
    }
  }
}
