package com.telemed.emr.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, UUID fileId, String description, Instant createdAt) {}
