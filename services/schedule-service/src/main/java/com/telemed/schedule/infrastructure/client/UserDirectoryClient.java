package com.telemed.schedule.infrastructure.client;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UserDirectoryClient {

  private final RestClient client;
  private final boolean enabled;

  public UserDirectoryClient(
      @Value("${schedule.user-service.base-url:http://user-service:8082}") String baseUrl,
      @Value("${schedule.user-service.internal-token:}") String internalToken,
      @Value("${schedule.user-service.validation-enabled:false}") boolean enabled) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader("X-Internal-Token", internalToken)
            .build();
    this.enabled = enabled;
  }

  public boolean enabled() {
    return enabled;
  }

  public Optional<InternalUser> find(UUID userId) {
    if (!enabled) {
      return Optional.empty();
    }
    try {
      InternalUser user =
          client.get().uri("/internal/users/{id}", userId).retrieve().body(InternalUser.class);
      return Optional.ofNullable(user);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw exception;
    }
  }

  public record InternalUser(
      UUID userId,
      String email,
      String firstName,
      String lastName,
      String displayName,
      Set<String> roles,
      String status,
      DoctorProfile doctorProfile) {}

  public record DoctorProfile(
      String specialization,
      String licenseNumber,
      boolean acceptingPatients,
      String verificationStatus,
      java.time.Instant verifiedAt,
      UUID verifiedBy) {}
}
