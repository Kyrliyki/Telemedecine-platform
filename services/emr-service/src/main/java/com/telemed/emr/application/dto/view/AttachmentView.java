package com.telemed.emr.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record AttachmentView(UUID id, UUID fileId, String description, Instant createdAt) {}
