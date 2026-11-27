package com.telemed.emr.infrastructure.client;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AppointmentValidationClient {

  private final RestClient client;
  private final boolean enabled;

  public AppointmentValidationClient(
      @Value("${emr.appointment-service.base-url:http://appointment-service:8083}") String baseUrl,
      @Value("${emr.appointment-service.internal-token:}") String internalToken,
      @Value("${emr.appointment-service.validation-enabled:false}") boolean enabled) {
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

  public Optional<AppointmentInfo> find(UUID appointmentId) {
    if (!enabled) {
      return Optional.empty();
    }
    try {
      AppointmentInfo info =
          client
              .get()
              .uri("/internal/appointments/{id}", appointmentId)
              .retrieve()
              .body(AppointmentInfo.class);
      return Optional.ofNullable(info);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw exception;
    }
  }

  public record AppointmentInfo(UUID appointmentId, UUID patientId, UUID doctorId, String status) {}
}
