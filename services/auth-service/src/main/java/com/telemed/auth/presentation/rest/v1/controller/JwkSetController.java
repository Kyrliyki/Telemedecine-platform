package com.telemed.auth.presentation.rest.v1.controller;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JwkSetController {

  private final JWKSource<SecurityContext> jwkSource;

  @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> jwks() throws Exception {
    List<JWK> keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null);
    JWKSet publicJwkSet = new JWKSet(keys.stream().map(JWK::toPublicJWK).toList());
    return publicJwkSet.toJSONObject();
  }
}
