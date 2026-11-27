package com.telemed.auth.application.service;

import com.telemed.auth.application.port.output.RefreshTokenRepository;
import com.telemed.auth.application.port.output.TokenRevocationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService implements TokenRevocationPort {

  private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
  private static final String KEY_PREFIX = "auth:revoked-before:";

  private final RefreshTokenRepository refreshTokenRepository;
  private final StringRedisTemplate redis;
  private final AuthAuditLogger audit;
  private final Duration accessTtl;

  public TokenRevocationService(
      RefreshTokenRepository refreshTokenRepository,
      StringRedisTemplate redis,
      AuthAuditLogger audit,
      @Value("${auth.access-ttl:15m}") Duration accessTtl) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.redis = redis;
    this.audit = audit;
    this.accessTtl = accessTtl;
  }

  @Override
  @Transactional
  public void revokeAllForUser(UUID userId, String reason) {
    if (userId == null) {
      return;
    }
    Instant now = Instant.now();
    int revoked = refreshTokenRepository.revokeAllByUserId(userId, now);
    long cutoff = now.toEpochMilli();
    try {
      redis
          .opsForValue()
          .set(KEY_PREFIX + userId, Long.toString(cutoff), accessTtl.plusSeconds(60));
    } catch (RuntimeException exception) {
      log.error("Failed to write token revocation cutoff userId={}", userId, exception);
    }
    audit.accessTokenRevoked(userId, reason);
    log.info("Revoked {} refresh tokens and access tokens for userId={}", revoked, userId);
  }
}
