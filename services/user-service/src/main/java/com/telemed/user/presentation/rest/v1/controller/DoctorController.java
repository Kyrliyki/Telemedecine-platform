package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.user.application.port.input.GetDoctorProfileUseCase;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorPageResponse;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorProfileResponse;
import com.telemed.user.presentation.rest.v1.mapper.UserWebMapper;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/doctors")
public class DoctorController {

  private final GetDoctorProfileUseCase getDoctorProfileUseCase;
  private final UserWebMapper mapper;

  public DoctorController(GetDoctorProfileUseCase getDoctorProfileUseCase, UserWebMapper mapper) {
    this.getDoctorProfileUseCase = getDoctorProfileUseCase;
    this.mapper = mapper;
  }

  @GetMapping
  public DoctorPageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String specialization) {
    return mapper.toDoctorPageResponse(getDoctorProfileUseCase.findAll(specialization, page, size));
  }

  @GetMapping("/{userId}")
  public DoctorProfileResponse get(@PathVariable UUID userId) {
    return mapper.toResponse(getDoctorProfileUseCase.getByUserId(userId));
  }
}
