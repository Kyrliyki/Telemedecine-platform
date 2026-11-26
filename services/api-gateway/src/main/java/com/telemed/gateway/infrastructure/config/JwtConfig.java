package com.telemed.gateway.infrastructure.config;

import com.telemed.gateway.infrastructure.security.GatewayAudienceValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
@EnableConfigurationProperties(GatewayJwtProperties.class)
public class JwtConfig {

  @Bean
  public ReactiveJwtDecoder reactiveJwtDecoder(GatewayJwtProperties properties) {
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();

    OAuth2TokenValidator<Jwt> validators =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(properties.clockSkew()),
            JwtValidators.createDefaultWithIssuer(properties.issuer()),
            new GatewayAudienceValidator(properties.audience()));
    decoder.setJwtValidator(validators);
    return decoder;
  }
}
