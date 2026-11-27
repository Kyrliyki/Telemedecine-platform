package com.telemed.user.infrastructure.time;

import com.telemed.user.application.port.output.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClock implements Clock {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
