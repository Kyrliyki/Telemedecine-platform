package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.model.ExternalIdentity;

public interface OAuthProviderClient {
  ExternalIdentity exchangeCode(String authorizationCode);
}
