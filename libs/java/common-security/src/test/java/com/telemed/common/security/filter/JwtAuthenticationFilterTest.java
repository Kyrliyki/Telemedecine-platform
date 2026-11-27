package com.telemed.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telemed.common.security.blacklist.TokenBlacklist;
import com.telemed.common.security.jwt.JwtAuthenticationConverter;
import com.telemed.common.security.jwt.TokenValidator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtAuthenticationFilterTest {

  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
  private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatesValidBearerToken() throws Exception {
    Jwt jwt = token("jti-1", List.of("DOCTOR"));
    when(decoder.decode("valid-token")).thenReturn(jwt);
    JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(decoder, new TokenValidator(blacklist, false), converter);

    MockHttpServletRequest request = request("valid-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication).isNotNull();
    assertThat(authentication.getName()).isEqualTo("user-1");
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_DOCTOR");
  }

  @Test
  void invalidTokenDoesNotAuthenticateButContinuesChain() throws Exception {
    when(decoder.decode("bad-token")).thenThrow(new JwtException("invalid"));
    JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(decoder, new TokenValidator(blacklist, false), converter);

    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request("bad-token"), new MockHttpServletResponse(), chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void tokenStoreFailureReturnsServiceUnavailable() throws Exception {
    Jwt jwt = token("jti-2", List.of("PATIENT"));
    when(decoder.decode("valid-token")).thenReturn(jwt);
    when(blacklist.isBlacklisted("jti-2")).thenThrow(new RuntimeException("redis is down"));

    JwtAuthenticationFilter filter =
        new JwtAuthenticationFilter(decoder, new TokenValidator(blacklist, true), converter);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request("valid-token"), response, chain);

    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(chain.getRequest()).isNull();
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static MockHttpServletRequest request(String token) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    return request;
  }

  private static Jwt token(String jti, List<String> roles) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-1")
        .jti(jti)
        .claim("roles", roles)
        .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
        .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
        .build();
  }
}
