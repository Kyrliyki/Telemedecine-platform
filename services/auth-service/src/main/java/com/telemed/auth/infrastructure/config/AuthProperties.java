package com.telemed.auth.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
    Duration refreshTtl,
    Duration accessTtl,
    Duration invitationTtl,
    Duration passwordResetTtl,
    Duration emailVerificationTtl,
    Duration oauthLinkConfirmationTtl,
    String frontendBaseUrl,
    String frontendOauthCallbackPath,
    Google google) {
  public record Google(
      String clientId,
      String clientSecret,
      String redirectUri,
      String jwkSetUri,
      String issuer,
      java.util.List<String> allowedDomains) {}
}
