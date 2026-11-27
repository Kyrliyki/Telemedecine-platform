package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.view.IntrospectionResult;

public interface TokenIntrospectionUseCase {
  IntrospectionResult introspect(String accessToken);
}
