package com.telemed.gateway.infrastructure.config;

import com.telemed.gateway.infrastructure.security.WebSocketBearerTokenConverter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  private static final String[] PUBLIC_PATHS = {
    "/api/v1/auth/login",
    "/api/v1/auth/register",
    "/api/v1/auth/refresh",
    "/api/v1/auth/logout",
    "/api/v1/auth/verify-email",
    "/api/v1/auth/accept-invitation",
    "/api/v1/auth/oauth/**",
    "/actuator/health/**",
    "/actuator/prometheus",
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/webjars/**",
    "/error"
  };

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, ReactiveJwtAuthenticationConverter jwtAuthenticationConverter) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(Customizer.withDefaults())
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers(PUBLIC_PATHS)
                    .permitAll()
                    .pathMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .pathMatchers("/ws/chat/**")
                    .authenticated()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .permitAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .bearerTokenConverter(new WebSocketBearerTokenConverter("/ws/chat"))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (exchange, exception) ->
                            writeProblem(
                                exchange,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Authentication required"))
                    .accessDeniedHandler(
                        (exchange, exception) ->
                            writeProblem(
                                exchange, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied")))
        .build();
  }

  @Bean
  public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
    ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          List<String> roles = jwt.getClaimAsStringList("roles");
          if (roles == null || roles.isEmpty()) {
            return Flux.empty();
          }
          return Flux.fromIterable(roles)
              .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role));
        });
    return converter;
  }

  private Mono<Void> writeProblem(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    var response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String body =
        "{\"status\":"
            + status.value()
            + ",\"code\":\""
            + code
            + "\",\"message\":\""
            + message
            + "\"}";
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
