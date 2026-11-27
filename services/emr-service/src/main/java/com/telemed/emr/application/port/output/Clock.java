package com.telemed.emr.application.port.output;

import java.time.Instant;

public interface Clock {

  Instant now();
}
