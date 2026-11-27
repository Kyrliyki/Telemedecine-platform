package com.telemed.emr.infrastructure.config;

import com.telemed.common.security.blacklist.NoOpTokenBlacklist;
import com.telemed.common.security.blacklist.TokenBlacklist;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BlacklistConfig {

  @Bean
  @ConditionalOnMissingBean(TokenBlacklist.class)
  public TokenBlacklist tokenBlacklist() {
    return new NoOpTokenBlacklist();
  }
}
