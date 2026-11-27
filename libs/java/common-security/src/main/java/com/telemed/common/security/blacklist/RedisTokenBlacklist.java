package com.telemed.common.security.blacklist;

import java.time.Duration;
import java.time.Instant;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisTokenBlacklist implements TokenBlacklist {

  private static final String KEY_PREFIX = "blacklist:";
  private final StringRedisTemplate redis;

  public RedisTokenBlacklist(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void add(String jti, Instant expiresAt) {
    Duration ttl = Duration.between(Instant.now(), expiresAt);
    if (ttl.isNegative() || ttl.isZero()) return;
    redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
  }

  @Override
  public boolean isBlacklisted(String jti) {
    return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
  }
}
