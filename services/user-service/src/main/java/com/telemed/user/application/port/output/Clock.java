package com.telemed.user.application.port.output;

import java.time.Instant;

public interface Clock {

  Instant now();
}
