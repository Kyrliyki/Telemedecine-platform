package com.telemed.common.security.jwt;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.client.RestTemplate;

public final class JwtDecoderFactory {
  private JwtDecoderFactory() {}

  public static JwtDecoder create(JwtProperties props) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    int timeoutMillis = Math.toIntExact(props.jwkSetTimeout().toMillis());
    requestFactory.setConnectTimeout(timeoutMillis);
    requestFactory.setReadTimeout(timeoutMillis);

    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri())
            .restOperations(new RestTemplate(requestFactory))
            .build();

    OAuth2TokenValidator<Jwt> validators =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(props.clockSkew()),
            JwtValidators.createDefaultWithIssuer(props.issuer()),
            new AudienceValidator(props.audience()));
    decoder.setJwtValidator(validators);

    return decoder;
  }
}
