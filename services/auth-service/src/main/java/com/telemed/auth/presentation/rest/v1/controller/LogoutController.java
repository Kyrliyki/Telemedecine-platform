package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.LogoutCommand;
import com.telemed.auth.application.port.input.LogoutUseCase;
import com.telemed.auth.presentation.rest.v1.dto.request.LogoutRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class LogoutController {

  private final LogoutUseCase logoutUseCase;

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) LogoutRequest body) {
    String refresh = body != null ? body.refreshToken() : null;
    String jti = jwt != null ? jwt.getId() : null;
    Instant exp = jwt != null ? jwt.getExpiresAt() : null;
    logoutUseCase.logout(new LogoutCommand(refresh, jti, exp));
    return ResponseEntity.noContent().build();
  }
}
