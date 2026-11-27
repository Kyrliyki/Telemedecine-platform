package com.telemed.auth.infrastructure.config;

import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.OAuthProviderClient;
import com.telemed.auth.infrastructure.security.GoogleOAuthProviderClientAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class GoogleOAuthConfig {

  @Bean
  public RestClient googleRestClient() {
    return RestClient.builder().baseUrl("https://oauth2.googleapis.com").build();
  }

  @Bean
  public OAuthProviderClient oauthProviderClient(
      RestClient googleRestClient, AuthProperties props, Clock clock) {
    return new GoogleOAuthProviderClientAdapter(googleRestClient, props.google(), clock);
  }
}
