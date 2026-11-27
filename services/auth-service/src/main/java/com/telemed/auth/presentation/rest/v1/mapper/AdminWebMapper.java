package com.telemed.auth.presentation.rest.v1.mapper;

import com.telemed.auth.application.dto.command.*;
import com.telemed.auth.application.dto.view.InvitationResult;
import com.telemed.auth.application.dto.view.UserRegistrationResult;
import com.telemed.auth.application.port.output.PageResult;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.presentation.rest.v1.dto.request.CreateUserByAdminRequest;
import com.telemed.auth.presentation.rest.v1.dto.request.InviteUserRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.AdminUserPageResponse;
import com.telemed.auth.presentation.rest.v1.dto.response.AdminUserResponse;
import com.telemed.auth.presentation.rest.v1.dto.response.InvitationResponse;
import com.telemed.auth.presentation.rest.v1.dto.response.UserRegistrationResponse;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminWebMapper {

  public CreateUserByAdminCommand toCommand(CreateUserByAdminRequest request, UUID actorId) {

    return new CreateUserByAdminCommand(request.email(), request.roles(), actorId);
  }

  public InviteUserCommand toCommand(InviteUserRequest request, UUID actorId) {
    return new InviteUserCommand(request.email(), request.roles(), actorId);
  }

  public UserRegistrationResponse toResponse(UserRegistrationResult result) {
    return new UserRegistrationResponse(
        result.userId(),
        result.email(),
        result.roles(),
        result.status(),
        result.invitationRequired());
  }

  public InvitationResponse toResponse(InvitationResult result) {
    return new InvitationResponse(result.userId(), result.email(), result.expiresAt());
  }

  public AdminUserResponse toResponse(AuthUser user) {
    return new AdminUserResponse(
        user.id().value(),
        user.email().value(),
        user.emailVerified(),
        user.status().name(),
        user.roles().stream().map(Enum::name).collect(Collectors.toSet()),
        user.createdAt(),
        user.updatedAt());
  }

  public AdminUserPageResponse toPageResponse(PageResult<AuthUser> result) {
    return new AdminUserPageResponse(
        result.content().stream().map(this::toResponse).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
