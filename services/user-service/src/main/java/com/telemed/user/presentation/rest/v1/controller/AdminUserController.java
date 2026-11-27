package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.user.application.dto.query.AdminUserQuery;
import com.telemed.user.application.port.input.ListUsersUseCase;
import com.telemed.user.presentation.rest.v1.dto.response.UserPageResponse;
import com.telemed.user.presentation.rest.v1.mapper.UserWebMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

  private final ListUsersUseCase listUsersUseCase;
  private final UserWebMapper mapper;

  public AdminUserController(ListUsersUseCase listUsersUseCase, UserWebMapper mapper) {
    this.listUsersUseCase = listUsersUseCase;
    this.mapper = mapper;
  }

  @GetMapping
  public UserPageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String email,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String role) {
    return mapper.toUserPageResponse(
        listUsersUseCase.findAll(new AdminUserQuery(page, size, email, status, role)));
  }
}
