package com.telemed.gateway.application.port.output;

import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface TokenBlacklistChecker {

  Mono<Boolean> isBlacklisted(String jti);

  /**
   * True when every access token issued for the user before {@code issuedAt} must be treated as
   * revoked (block/delete/role change).
   */
  Mono<Boolean> isUserRevokedBefore(UUID userId, Instant issuedAt);
}
