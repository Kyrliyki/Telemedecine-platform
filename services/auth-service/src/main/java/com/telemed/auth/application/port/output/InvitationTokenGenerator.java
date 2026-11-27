package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.TokenHash;

public interface InvitationTokenGenerator {

  Generated generate();

  TokenHash hash(String rawToken);

  record Generated(String rawToken, TokenHash tokenHash) {}
}
