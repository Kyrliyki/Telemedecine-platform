package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.command.LoginWithOAuthCommand;
import com.telemed.auth.application.dto.command.OAuthLinkConfirmationCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.application.port.input.LoginWithOAuthUseCase;
import com.telemed.auth.application.port.input.OAuthLinkConfirmationUseCase;
import com.telemed.auth.domain.exception.OAuthAccountConflictException;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.infrastructure.config.AuthProperties;
import com.telemed.auth.presentation.rest.v1.dto.request.OAuthConfirmRequest;
import com.telemed.auth.presentation.rest.v1.dto.request.OAuthLoginRequest;
import com.telemed.auth.presentation.rest.v1.dto.response.TokenResponse;
import com.telemed.auth.presentation.rest.v1.mapper.AuthWebMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

  private static final String DEFAULT_FRONTEND_CALLBACK_PATH = "/oauth/callback";

  private final LoginWithOAuthUseCase loginWithOAuthUseCase;
  private final OAuthLinkConfirmationUseCase oauthLinkConfirmationUseCase;
  private final AuthWebMapper mapper;
  private final AuthProperties properties;

  @PostMapping("/google")
  public ResponseEntity<TokenResponse> google(@Valid @RequestBody OAuthLoginRequest request) {
    var result =
        loginWithOAuthUseCase.loginWithGoogle(
            new LoginWithOAuthCommand(request.authorizationCode(), request.redirectUri()));
    return ResponseEntity.ok(mapper.toResponse(result));
  }

  @PostMapping("/confirm")
  public ResponseEntity<Void> confirm(@Valid @RequestBody OAuthConfirmRequest request) {
    oauthLinkConfirmationUseCase.confirm(
        new OAuthLinkConfirmationCommand(request.token(), parseProvider(request.provider())));
    return ResponseEntity.noContent().build();
  }

  private static ProviderType parseProvider(String raw) {
    ProviderType provider;
    try {
      provider = ProviderType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unsupported OAuth provider: " + raw);
    }
    if (!provider.isExternal()) {
      throw new IllegalArgumentException("OAuth provider must be external");
    }
    return provider;
  }

  @GetMapping("/google/callback")
  public ResponseEntity<Void> googleCallback(
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String error) {

    if (error != null && !error.isBlank()) {
      return redirectToFrontend(errorFragment(error, state));
    }
    if (code == null || code.isBlank()) {
      return redirectToFrontend(errorFragment("missing_code", state));
    }

    try {
      TokenPairResult result =
          loginWithOAuthUseCase.loginWithGoogle(new LoginWithOAuthCommand(code, null));
      return redirectToFrontend(successFragment(result, state));
    } catch (OAuthAccountConflictException ex) {
      return redirectToFrontend(errorFragment("oauth_account_conflict", state));
    } catch (RuntimeException ex) {
      log.warn("Google OAuth callback failed", ex);
      return redirectToFrontend(errorFragment("oauth_failed", state));
    }
  }

  private ResponseEntity<Void> redirectToFrontend(String fragment) {
    String base = properties.frontendBaseUrl();
    if (base == null || base.isBlank()) {
      base = "http://localhost:3000";
    }
    base = base.replaceAll("/+$", "");

    String path = properties.frontendOauthCallbackPath();
    if (path == null || path.isBlank()) {
      path = DEFAULT_FRONTEND_CALLBACK_PATH;
    }
    if (!path.startsWith("/")) {
      path = "/" + path;
    }

    URI location = URI.create(base + path + "#" + fragment);
    return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
  }

  private static String successFragment(TokenPairResult result, String state) {
    StringBuilder fragment =
        new StringBuilder()
            .append("access_token=")
            .append(encode(result.accessToken()))
            .append("&refresh_token=")
            .append(encode(result.refreshToken()))
            .append("&access_expires_at=")
            .append(encode(asString(result.accessExpiresAt())))
            .append("&refresh_expires_at=")
            .append(encode(asString(result.refreshExpiresAt())))
            .append("&token_type=Bearer");
    appendState(fragment, state);
    return fragment.toString();
  }

  private static String errorFragment(String error, String state) {
    StringBuilder fragment = new StringBuilder("error=").append(encode(error));
    appendState(fragment, state);
    return fragment.toString();
  }

  private static void appendState(StringBuilder fragment, String state) {
    if (state != null && !state.isBlank()) {
      fragment.append("&state=").append(encode(state));
    }
  }

  private static String asString(Object value) {
    return value == null ? "" : value.toString();
  }

  private static String encode(String value) {
    return value == null ? "" : UriUtils.encode(value, StandardCharsets.UTF_8);
  }
}
