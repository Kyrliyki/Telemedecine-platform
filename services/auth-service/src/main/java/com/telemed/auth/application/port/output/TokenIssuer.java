package com.telemed.auth.application.port.output;

import com.telemed.auth.application.dto.view.IntrospectionResult;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.domain.model.AuthUser;

public interface TokenIssuer {
  TokenPairResult issue(AuthUser user);

  String issueAccessToken(AuthUser user);

  IntrospectionResult introspect(String accessToken);
}
