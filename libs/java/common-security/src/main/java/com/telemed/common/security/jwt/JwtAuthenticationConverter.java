package com.telemed.common.security.jwt;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class JwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {
  private static final String ROLES_CLAIM = "roles";
  private static final String ROLE_PREFIX = "ROLE_";

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
    Set<String> roles =
        jwt.getClaimAsStringList(ROLES_CLAIM) == null
            ? Set.of()
            : Set.copyOf(jwt.getClaimAsStringList(ROLES_CLAIM));

    return roles.stream()
        .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
        .collect(Collectors.toUnmodifiableSet());
  }
}
