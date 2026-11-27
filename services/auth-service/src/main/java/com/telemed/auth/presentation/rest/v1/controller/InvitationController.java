package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.AcceptInvitationCommand;
import com.telemed.auth.application.port.input.AcceptInvitationUseCase;
import com.telemed.auth.presentation.rest.v1.dto.request.AcceptInvitationRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.UserRegistrationResponse;
import com.telemed.auth.presentation.rest.v1.mapper.AuthWebMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class InvitationController {

  private final AcceptInvitationUseCase acceptInvitationUseCase;
  private final AuthWebMapper mapper;

  @PostMapping("/accept-invitation")
  public ResponseEntity<UserRegistrationResponse> accept(
      @Valid @RequestBody AcceptInvitationRequest request) {
    var result =
        acceptInvitationUseCase.accept(
            new AcceptInvitationCommand(request.token(), request.password()));
    return ResponseEntity.ok(mapper.toResponse(result));
  }
}
