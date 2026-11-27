package com.telemed.auth.presentation.rest.v1.mapper;

import com.telemed.auth.application.dto.command.*;
import com.telemed.auth.application.dto.view.*;
import com.telemed.auth.presentation.rest.v1.dto.request.LoginRequest;
import com.telemed.auth.presentation.rest.v1.dto.request.RegisterRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.TokenResponse;
import com.telemed.auth.presentation.rest.v1.dto.response.UserRegistrationResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthWebMapper {

  public RegisterLocalCommand toCommand(RegisterRequest request) {
    return new RegisterLocalCommand(request.email(), request.password());
  }

  public LoginLocalCommand toCommand(LoginRequest request) {
    return new LoginLocalCommand(request.email(), request.password());
  }

  public TokenResponse toResponse(TokenPairResult result) {
    return new TokenResponse(
        result.accessToken(),
        result.refreshToken(),
        result.accessExpiresAt(),
        result.refreshExpiresAt(),
        false);
  }

  public UserRegistrationResponse toResponse(UserRegistrationResult result) {
    return new UserRegistrationResponse(
        result.userId(),
        result.email(),
        result.roles(),
        result.status(),
        result.invitationRequired());
  }
}
