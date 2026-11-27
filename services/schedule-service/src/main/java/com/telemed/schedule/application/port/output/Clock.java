package com.telemed.schedule.application.port.output;

import java.time.Instant;

public interface Clock {

  Instant now();
}
