package com.telemed.emr.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddAttachmentRequest(@NotNull UUID fileId, @Size(max = 255) String description) {}
