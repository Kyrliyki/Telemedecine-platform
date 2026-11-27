package com.telemed.auth.application.port.output;

import java.util.UUID;

public interface TokenRevocationPort {

  void revokeAllForUser(UUID userId, String reason);
}
