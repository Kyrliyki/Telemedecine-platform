package com.telemed.gateway.infrastructure.security;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Resolves bearer tokens from the {@code Authorization} header and, for WebSocket
 * handshakes only, from the {@code token} query parameter.
 *
 * <p>Browsers cannot set custom headers on {@code new WebSocket(...)}, so the token has to
 * travel in the URL. Accepting {@code ?token=} on every path would leak access tokens into
 * proxy access logs and browser history for regular requests, therefore the query parameter
 * is honoured only for the configured WebSocket path prefixes.
 */
public final class WebSocketBearerTokenConverter implements ServerAuthenticationConverter {

  private static final String TOKEN_PARAMETER = "token";

  private final ServerAuthenticationConverter headerConverter =
      new ServerBearerTokenAuthenticationConverter();
  private final String[] webSocketPathPrefixes;

  public WebSocketBearerTokenConverter(String... webSocketPathPrefixes) {
    this.webSocketPathPrefixes = webSocketPathPrefixes.clone();
  }

  @Override
  public Mono<Authentication> convert(ServerWebExchange exchange) {
    return headerConverter
        .convert(exchange)
        .switchIfEmpty(Mono.defer(() -> fromQueryParameter(exchange)));
  }

  private Mono<Authentication> fromQueryParameter(ServerWebExchange exchange) {
    if (!isWebSocketPath(exchange.getRequest())) {
      return Mono.empty();
    }
    String token = exchange.getRequest().getQueryParams().getFirst(TOKEN_PARAMETER);
    if (token == null || token.isBlank()) {
      return Mono.empty();
    }
    return Mono.just(new BearerTokenAuthenticationToken(token));
  }

  private boolean isWebSocketPath(ServerHttpRequest request) {
    String path = request.getPath().value();
    for (String prefix : webSocketPathPrefixes) {
      if (path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
