package com.telemed.schedule.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReleaseSlotRequest(@NotNull UUID appointmentId) {}
