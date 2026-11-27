package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.RefreshTokenCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.application.exception.InvalidCredentialsException;
import com.telemed.auth.application.policy.RefreshTokenPolicy;
import com.telemed.auth.application.port.input.RefreshTokenUseCase;
import com.telemed.auth.application.port.output.*;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenUseCase {
  private final RefreshTokenRepository refreshTokenRepository;
  private final AuthUserRepository authUserRepository;
  private final TokenIssuer tokenIssuer;
  private final TokenHasher tokenHasher;
  private final RefreshTokenPolicy policy;
  private final AuthAuditLogger audit;
  private final Clock clock;

  @Override
  @Transactional(noRollbackFor = InvalidCredentialsException.class)
  public TokenPairResult refresh(RefreshTokenCommand command) {
    var hash = tokenHasher.hash(command.rawRefreshToken());
    RefreshToken token =
        refreshTokenRepository.findByTokenHash(hash).orElseThrow(InvalidCredentialsException::new);

    if (token.revoked()) {
      refreshTokenRepository.revokeFamily(token.familyId());
      audit.refreshReuseDetected(token.userId().value());
      throw new InvalidCredentialsException();
    }
    if (!token.isActive(clock.now())) {
      throw new InvalidCredentialsException();
    }

    AuthUser user =
        authUserRepository.findById(token.userId()).orElseThrow(InvalidCredentialsException::new);
    user.ensureCanAuthenticate();

    var newHash = tokenHasher.hashNew();
    RefreshToken rotated = token.rotate(newHash.hash(), clock.now(), policy.refreshTtl());
    refreshTokenRepository.save(token);
    refreshTokenRepository.save(rotated);

    audit.tokenRefreshed(user.id().value());
    return new TokenPairResult(
        tokenIssuer.issueAccessToken(user),
        newHash.rawToken(),
        clock.now().plus(policy.accessTtl()),
        rotated.expiresAt());
  }
}
