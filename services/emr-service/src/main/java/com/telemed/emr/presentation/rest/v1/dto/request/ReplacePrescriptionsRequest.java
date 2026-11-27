package com.telemed.emr.presentation.rest.v1.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReplacePrescriptionsRequest(List<@Valid Entry> entries) {

  public record Entry(
      @NotBlank @Size(max = 255) String medicationName,
      @NotBlank @Size(max = 120) String dosage,
      @NotBlank @Size(max = 120) String frequency,
      @Min(1) @Max(365) int durationDays,
      @Size(max = 1000) String instructions) {}
}
