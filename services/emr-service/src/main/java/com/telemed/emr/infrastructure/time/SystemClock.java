package com.telemed.emr.infrastructure.time;

import com.telemed.emr.application.port.output.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SystemClock implements Clock {

  @Override
  public Instant now() {
    return Instant.now();
  }
}
