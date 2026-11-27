package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;

public interface EmailVerificationTokenGenerator {

  Generated generate(UserId userId, Instant now);

  EmailVerificationTokenRepository.Stored hash(String rawToken, Instant now);

  record Generated(String rawToken, EmailVerificationTokenRepository.Stored stored) {}
}
