package com.telemed.auth.presentation.rest.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.dto.command.LoginWithOAuthCommand;
import com.telemed.auth.application.dto.command.OAuthLinkConfirmationCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.application.port.input.LoginWithOAuthUseCase;
import com.telemed.auth.application.port.input.OAuthLinkConfirmationUseCase;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.infrastructure.config.AuthProperties;
import com.telemed.auth.presentation.rest.v1.dto.request.OAuthConfirmRequest;
import com.telemed.auth.presentation.rest.v1.mapper.AuthWebMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

  @Mock LoginWithOAuthUseCase loginWithOAuthUseCase;
  @Mock OAuthLinkConfirmationUseCase oauthLinkConfirmationUseCase;

  private OAuthController controller;

  @BeforeEach
  void setUp() {
    AuthProperties properties =
        new AuthProperties(
            null, null, null, null, null, null, "http://localhost:3000", "/oauth/callback", null);
    controller =
        new OAuthController(
            loginWithOAuthUseCase, oauthLinkConfirmationUseCase, new AuthWebMapper(), properties);
  }

  @Test
  void redirectsToFrontendWithTokensInFragment() {
    when(loginWithOAuthUseCase.loginWithGoogle(any(LoginWithOAuthCommand.class)))
        .thenReturn(
            new TokenPairResult(
                "access-token",
                "refresh-token",
                Instant.parse("2026-01-01T11:00:00Z"),
                Instant.parse("2026-01-31T10:00:00Z")));

    var response = controller.googleCallback("code", "state-1", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    String location = response.getHeaders().getLocation().toString();
    assertThat(location)
        .startsWith("http://localhost:3000/oauth/callback#")
        .contains("access_token=access-token")
        .contains("refresh_token=refresh-token")
        .contains("token_type=Bearer")
        .contains("state=state-1");
  }

  @Test
  void confirmsOAuthLink() {
    var response = controller.confirm(new OAuthConfirmRequest("token-1", "google"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(oauthLinkConfirmationUseCase)
        .confirm(new OAuthLinkConfirmationCommand("token-1", ProviderType.GOOGLE));
  }

  @Test
  void redirectsGoogleErrorToFrontend() {
    var response = controller.googleCallback(null, null, "access_denied");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo("http://localhost:3000/oauth/callback#error=access_denied");
  }

  @Test
  void redirectsMissingCodeErrorToFrontend() {
    var response = controller.googleCallback(null, "state-1", null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString())
        .isEqualTo("http://localhost:3000/oauth/callback#error=missing_code&state=state-1");
  }
}
