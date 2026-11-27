package com.telemed.schedule.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BulkAvailabilityRequest(
    @NotNull List<UUID> doctorIds, @NotNull LocalDate from, @NotNull LocalDate to, int limit) {}
