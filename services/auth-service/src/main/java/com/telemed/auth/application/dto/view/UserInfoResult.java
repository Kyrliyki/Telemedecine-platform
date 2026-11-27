package com.telemed.auth.application.dto.view;

import java.util.List;
import java.util.UUID;

public record UserInfoResult(UUID id, String email, List<String> roles) {}
