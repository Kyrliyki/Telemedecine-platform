package com.telemed.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtDecoderFactoryTest {

  private HttpServer server;
  private RSAKey rsaKey;

  @BeforeEach
  void setUp() throws Exception {
    rsaKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    JWKSet publicJwks = new JWKSet(rsaKey.toPublicJWK());
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/oauth2/jwks",
        exchange -> {
          byte[] body = publicJwks.toString().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void decodesTokenSignedByConfiguredJwks() throws Exception {
    JwtEncoder encoder =
        new NimbusJwtEncoder(
            new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(rsaKey)));
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("auth-service")
            .subject("user-1")
            .audience(List.of("telemedicine"))
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(), claims))
            .getTokenValue();

    JwtDecoder decoder =
        JwtDecoderFactory.create(
            new JwtProperties(
                "auth-service",
                "http://localhost:" + server.getAddress().getPort() + "/oauth2/jwks",
                List.of("telemedicine"),
                Duration.ofSeconds(30),
                false,
                Duration.ofSeconds(5)));

    Jwt decoded = decoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo("user-1");
    assertThat(decoded.getAudience()).contains("telemedicine");
  }
}
