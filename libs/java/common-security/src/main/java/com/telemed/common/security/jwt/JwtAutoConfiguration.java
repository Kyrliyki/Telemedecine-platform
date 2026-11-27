package com.telemed.common.security.jwt;

import com.telemed.common.security.blacklist.TokenBlacklist;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(
    prefix = "security.jwt",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class JwtAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean
  public JwtDecoder jwtDecoder(JwtProperties props) {
    return JwtDecoderFactory.create(props);
  }

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    return new JwtAuthenticationConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenValidator tokenValidator(TokenBlacklist blacklist, JwtProperties props) {
    return new TokenValidator(blacklist, props.blacklistEnabled());
  }
}
