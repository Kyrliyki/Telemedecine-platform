package com.telemed.auth.infrastructure.config;

import com.telemed.auth.application.policy.InvitationPolicy;
import com.telemed.auth.application.policy.RefreshTokenPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PolicyConfig {

  @Bean
  public RefreshTokenPolicy refreshTokenPolicy(AuthProperties props) {
    return new RefreshTokenPolicy(props.refreshTtl(), props.accessTtl());
  }

  @Bean
  public InvitationPolicy invitationPolicy(AuthProperties props) {
    return new InvitationPolicy(props.invitationTtl());
  }
}
