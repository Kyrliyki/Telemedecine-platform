package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.LogoutCommand;
import com.telemed.auth.application.port.input.LogoutUseCase;
import com.telemed.auth.application.port.output.RefreshTokenRepository;
import com.telemed.auth.application.port.output.TokenHasher;
import com.telemed.common.security.blacklist.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LogoutService implements LogoutUseCase {
  private final RefreshTokenRepository refreshTokenRepository;
  private final TokenBlacklist tokenBlacklist;
  private final TokenHasher tokenHasher;
  private final AuthAuditLogger audit;

  @Override
  public void logout(LogoutCommand command) {
    if (command.rawRefreshToken() != null) {
      var hash = tokenHasher.hash(command.rawRefreshToken());
      refreshTokenRepository
          .findByTokenHash(hash)
          .ifPresent(
              token -> {
                token.revoke(java.time.Instant.now());
                refreshTokenRepository.save(token);
                audit.logout(token.userId().value());
              });
    }
    if (command.accessTokenJti() != null && command.accessTokenExp() != null) {
      tokenBlacklist.add(command.accessTokenJti(), command.accessTokenExp());
    }
  }
}
