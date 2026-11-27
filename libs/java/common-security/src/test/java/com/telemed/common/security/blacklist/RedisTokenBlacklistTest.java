package com.telemed.common.security.blacklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisTokenBlacklistTest {

  @SuppressWarnings("unchecked")
  private final StringRedisTemplate redis = Mockito.mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);

  @Test
  void storesKeyWithTtlForActiveToken() {
    when(redis.opsForValue()).thenReturn(values);
    RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redis);

    blacklist.add("jti-1", Instant.now().plusSeconds(60));

    verify(values)
        .set(
            org.mockito.ArgumentMatchers.eq("blacklist:jti-1"),
            org.mockito.ArgumentMatchers.eq("1"),
            any(Duration.class));
  }

  @Test
  void doesNotStoreExpiredToken() {
    RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redis);

    blacklist.add("jti-1", Instant.now().minusSeconds(1));

    verify(redis, never()).opsForValue();
  }

  @Test
  void checksExistenceByJti() {
    when(redis.hasKey("blacklist:jti-1")).thenReturn(true);
    RedisTokenBlacklist blacklist = new RedisTokenBlacklist(redis);

    assertThat(blacklist.isBlacklisted("jti-1")).isTrue();
  }
}
