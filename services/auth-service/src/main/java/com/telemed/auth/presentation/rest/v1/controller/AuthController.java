package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.port.input.LoginLocalUserUseCase;
import com.telemed.auth.application.port.input.RegisterLocalUserUseCase;
import com.telemed.auth.presentation.rest.v1.dto.request.LoginRequest;
import com.telemed.auth.presentation.rest.v1.dto.request.RegisterRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.TokenResponse;
import com.telemed.auth.presentation.rest.v1.dto.response.UserRegistrationResponse;
import com.telemed.auth.presentation.rest.v1.mapper.AuthWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final RegisterLocalUserUseCase registerUserUseCase;
  private final LoginLocalUserUseCase loginUseCase;
  private final AuthWebMapper mapper;

  @PostMapping("/register")
  public ResponseEntity<UserRegistrationResponse> register(
      @Valid @RequestBody RegisterRequest request) {
    var result = registerUserUseCase.register(mapper.toCommand(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
  }

  @PostMapping("/login")
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(mapper.toResponse(loginUseCase.login(mapper.toCommand(request))));
  }
}
