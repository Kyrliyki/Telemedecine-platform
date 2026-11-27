package com.telemed.auth.infrastructure.time;

import com.telemed.auth.application.port.output.Clock;
import java.time.Instant;

public class SystemClock implements Clock {
  @Override
  public Instant now() {
    return Instant.now();
  }
}
