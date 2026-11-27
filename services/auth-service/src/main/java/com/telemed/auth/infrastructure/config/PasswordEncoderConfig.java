package com.telemed.auth.infrastructure.config;

import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.infrastructure.security.BcryptPasswordEncoderAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class PasswordEncoderConfig {
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BcryptPasswordEncoderAdapter(new BCryptPasswordEncoder(12));
  }
}
