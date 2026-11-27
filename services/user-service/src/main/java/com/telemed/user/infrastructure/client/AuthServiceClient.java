package com.telemed.user.infrastructure.client;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthServiceClient {

  private final RestClient client;
  private final AuthServiceProperties properties;

  public AuthServiceClient(AuthServiceProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(java.time.Duration.ofSeconds(5));
    requestFactory.setReadTimeout(java.time.Duration.ofSeconds(15));
    this.client =
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .defaultHeader("X-Internal-Token", properties.internalToken())
            .build();
  }

  public InternalUserPage fetchPage(int page, int size) {
    return client
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .path("/internal/users")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .build())
        .retrieve()
        .body(InternalUserPage.class);
  }

  public AuthServiceProperties properties() {
    return properties;
  }

  public record InternalUserPage(
      List<InternalUser> content, long totalElements, int page, int size, int totalPages) {}

  public record InternalUser(
      UUID userId,
      String email,
      boolean emailVerified,
      String status,
      Set<String> roles,
      Instant createdAt,
      Instant updatedAt) {}
}
