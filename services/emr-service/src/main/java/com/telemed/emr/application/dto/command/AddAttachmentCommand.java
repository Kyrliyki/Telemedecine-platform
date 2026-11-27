package com.telemed.emr.application.dto.command;

import java.util.UUID;

public record AddAttachmentCommand(UUID fileId, String description) {}
