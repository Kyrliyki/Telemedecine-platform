package com.telemed.auth.infrastructure.config;

import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.infrastructure.time.SystemClock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return new SystemClock();
  }
}
