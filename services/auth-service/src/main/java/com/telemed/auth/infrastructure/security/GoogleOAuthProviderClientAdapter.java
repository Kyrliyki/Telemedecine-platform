package com.telemed.auth.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.OAuthProviderClient;
import com.telemed.auth.domain.model.ExternalIdentity;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.ProviderSubject;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.infrastructure.config.AuthProperties;
import java.util.Map;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

public class GoogleOAuthProviderClientAdapter implements OAuthProviderClient {

  private final RestClient restClient;
  private final AuthProperties.Google config;
  private final Clock clock;
  private final NimbusJwtDecoder idTokenDecoder;

  public GoogleOAuthProviderClientAdapter(
      RestClient restClient, AuthProperties.Google config, Clock clock) {
    this.restClient = restClient;
    this.config = config;
    this.clock = clock;

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri()).build();
    OAuth2TokenValidator<Jwt> audienceValidator = this::validateAudience;
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(config.issuer()), audienceValidator));
    this.idTokenDecoder = decoder;
  }

  @Override
  public ExternalIdentity exchangeCode(String authorizationCode) {
    var response =
        restClient
            .post()
            .uri("/token")
            .body(
                Map.of(
                    "code", authorizationCode,
                    "client_id", config.clientId(),
                    "client_secret", config.clientSecret(),
                    "redirect_uri", config.redirectUri(),
                    "grant_type", "authorization_code"))
            .retrieve()
            .body(GoogleTokenResponse.class);

    if (response == null || response.idToken() == null || response.idToken().isBlank()) {
      throw new IllegalStateException("Google token response does not contain id_token");
    }

    var idToken = decodeIdToken(response.idToken());
    if (config.allowedDomains() != null
        && !config.allowedDomains().isEmpty()
        && !config.allowedDomains().contains(idToken.hd())) {
      throw new IllegalArgumentException("OAuth domain not allowed: " + idToken.hd());
    }

    return ExternalIdentity.link(
        ProviderType.GOOGLE,
        new ProviderSubject(idToken.sub()),
        new Email(idToken.email()),
        idToken.emailVerified(),
        clock.now());
  }

  private GoogleIdToken decodeIdToken(String idToken) {
    Jwt jwt = idTokenDecoder.decode(idToken);
    String email = jwt.getClaimAsString("email");
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Google id_token does not contain email");
    }
    Boolean emailVerified = jwt.getClaim("email_verified");
    return new GoogleIdToken(
        jwt.getSubject(), email, Boolean.TRUE.equals(emailVerified), jwt.getClaimAsString("hd"));
  }

  private OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
    if (config.clientId() != null
        && jwt.getAudience() != null
        && jwt.getAudience().contains(config.clientId())) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Google id_token audience mismatch", null));
  }

  public record GoogleTokenResponse(
      @JsonProperty("access_token") String accessToken, @JsonProperty("id_token") String idToken) {}

  public record GoogleIdToken(
      String sub, String email, @JsonProperty("email_verified") boolean emailVerified, String hd) {}
}
