package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.VerifyEmailCommand;
import com.telemed.auth.application.port.input.VerifyEmailUseCase;
import com.telemed.auth.presentation.rest.v1.dto.request.VerifyEmailRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class VerifyEmailController {

  private final VerifyEmailUseCase verifyEmailUseCase;

  @PostMapping("/verify-email")
  public ResponseEntity<Void> verify(@Valid @RequestBody VerifyEmailRequest request) {
    verifyEmailUseCase.verify(new VerifyEmailCommand(request.token()));
    return ResponseEntity.noContent().build();
  }
}
