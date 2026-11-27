package com.telemed.schedule.presentation.rest.v1.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BulkAvailabilityResponse(Map<UUID, List<SlotResponse>> doctors) {}
