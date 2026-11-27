package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.RefreshTokenCommand;
import com.telemed.auth.application.port.input.RefreshTokenUseCase;
import com.telemed.auth.presentation.rest.v1.dto.request.RefreshRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.TokenResponse;
import com.telemed.auth.presentation.rest.v1.mapper.AuthWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class RefreshController {

  private final RefreshTokenUseCase refreshTokenUseCase;
  private final AuthWebMapper mapper;

  @PostMapping("/refresh")
  public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    var result = refreshTokenUseCase.refresh(new RefreshTokenCommand(request.refreshToken()));
    return ResponseEntity.ok(mapper.toResponse(result));
  }
}
