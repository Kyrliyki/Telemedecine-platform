package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.query.AdminUserQuery;
import com.telemed.auth.application.port.input.AdminUserQueryUseCase;
import com.telemed.auth.presentation.rest.v1.dto.response.AdminUserPageResponse;
import com.telemed.auth.presentation.rest.v1.mapper.AdminWebMapper;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/users")
@Hidden
@ConditionalOnProperty(
    prefix = "security.internal",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class InternalUserController {

  private final AdminUserQueryUseCase adminUserQueryUseCase;
  private final AdminWebMapper mapper;

  @GetMapping
  public AdminUserPageResponse list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
    return mapper.toPageResponse(
        adminUserQueryUseCase.findAll(new AdminUserQuery(page, size, null, null, null)));
  }
}
