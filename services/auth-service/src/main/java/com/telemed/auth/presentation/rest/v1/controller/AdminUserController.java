package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.*;
import com.telemed.auth.application.dto.query.AdminUserQuery;
import com.telemed.auth.application.port.input.*;
import com.telemed.auth.presentation.rest.v1.dto.request.*;
import com.telemed.auth.presentation.rest.v1.dto.response.*;
import com.telemed.auth.presentation.rest.v1.mapper.AdminWebMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final CreateUserByAdminUseCase createUserByAdminUseCase;
  private final InviteUserUseCase inviteUserUseCase;
  private final AssignRoleUseCase assignRoleUseCase;
  private final BlockUserUseCase blockUserUseCase;
  private final AdminUserQueryUseCase adminUserQueryUseCase;
  private final AdminWebMapper mapper;

  @GetMapping
  public AdminUserPageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role) {
    return mapper.toPageResponse(
        adminUserQueryUseCase.findAll(new AdminUserQuery(page, size, email, status, role)));
  }

  @GetMapping("/{userId}")
  public AdminUserResponse get(@PathVariable UUID userId) {
    return mapper.toResponse(adminUserQueryUseCase.findById(userId));
  }

  @PostMapping
  public ResponseEntity<UserRegistrationResponse> createByAdmin(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserByAdminRequest request) {
    UUID actorId = actorId(jwt);
    var result = createUserByAdminUseCase.createByAdmin(mapper.toCommand(request, actorId));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
  }

  @PostMapping("/invite")
  public ResponseEntity<InvitationResponse> invite(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InviteUserRequest request) {
    UUID actorId = actorId(jwt);
    var result = inviteUserUseCase.invite(mapper.toCommand(request, actorId));
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(result));
  }

  @PostMapping("/{userId}/roles")
  public ResponseEntity<Void> assignRole(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody AssignRoleRequest request) {
    UUID actorId = actorId(jwt);
    assignRoleUseCase.assignRole(new AssignRoleCommand(actorId, userId, request.role()));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{userId}/roles/{role}")
  public ResponseEntity<Void> revokeRole(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId, @PathVariable String role) {
    UUID actorId = actorId(jwt);
    assignRoleUseCase.revokeRole(new AssignRoleCommand(actorId, userId, role));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{userId}/block")
  public ResponseEntity<Void> block(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody BlockUserRequest request) {
    UUID actorId = actorId(jwt);
    blockUserUseCase.block(new BlockUserCommand(actorId, userId, request.reason()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{userId}/unblock")
  public ResponseEntity<Void> unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    UUID actorId = actorId(jwt);
    blockUserUseCase.unblock(new BlockUserCommand(actorId, userId, null));
    return ResponseEntity.noContent().build();
  }

  private UUID actorId(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Authenticated JWT is required");
    }
    return UUID.fromString(jwt.getSubject());
  }
}
