package com.telemed.auth.application.port.output;

import java.time.Instant;

public interface Clock {
  Instant now();
}
