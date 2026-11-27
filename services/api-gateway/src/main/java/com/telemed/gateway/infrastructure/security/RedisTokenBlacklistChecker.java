package com.telemed.gateway.infrastructure.security;

import com.telemed.gateway.application.port.output.TokenBlacklistChecker;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RedisTokenBlacklistChecker implements TokenBlacklistChecker {

  private static final String BLACKLIST_PREFIX = "blacklist:";
  private static final String USER_REVOKED_PREFIX = "auth:revoked-before:";

  private final ReactiveStringRedisTemplate redis;

  @Override
  public Mono<Boolean> isBlacklisted(String jti) {
    return redis.hasKey(BLACKLIST_PREFIX + jti);
  }

  @Override
  public Mono<Boolean> isUserRevokedBefore(UUID userId, Instant issuedAt) {
    if (userId == null || issuedAt == null) {
      return Mono.just(false);
    }
    return redis
        .opsForValue()
        .get(USER_REVOKED_PREFIX + userId)
        .map(value -> parseMillis(value) > issuedAt.toEpochMilli())
        .defaultIfEmpty(false);
  }

  private static long parseMillis(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }
}
