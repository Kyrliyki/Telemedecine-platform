package com.telemed.gateway.infrastructure.security;

import com.telemed.gateway.application.port.output.CurrentUserReader;
import com.telemed.gateway.domain.model.GatewayUser;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ReactiveSecurityCurrentUserReader implements CurrentUserReader {

  @Override
  public Mono<GatewayUser> read() {
    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .filter(authentication -> authentication.getPrincipal() instanceof Jwt)
        .map(authentication -> (Jwt) authentication.getPrincipal())
        .map(this::toGatewayUser);
  }

  private GatewayUser toGatewayUser(Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    return new GatewayUser(
        jwt.getSubject(),
        jwt.getClaimAsString("email"),
        roles == null ? Set.of() : Set.copyOf(roles),
        jwt.getId(),
        jwt.getExpiresAt());
  }
}
