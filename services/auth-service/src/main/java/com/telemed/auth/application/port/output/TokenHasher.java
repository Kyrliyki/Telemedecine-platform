package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.TokenHash;

public interface TokenHasher {
  TokenHash hash(String rawToken);

  HashedNew hashNew();

  record HashedNew(String rawToken, TokenHash hash) {}
}
