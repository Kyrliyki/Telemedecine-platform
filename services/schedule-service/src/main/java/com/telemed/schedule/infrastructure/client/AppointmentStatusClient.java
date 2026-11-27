package com.telemed.schedule.infrastructure.client;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AppointmentStatusClient {

  private final RestClient client;

  public AppointmentStatusClient(
      @Value("${schedule.appointment-service.base-url:http://appointment-service:8083}")
          String baseUrl,
      @Value("${schedule.appointment-service.internal-token:}") String internalToken) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Duration.ofSeconds(3));
    requestFactory.setReadTimeout(Duration.ofSeconds(5));
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .defaultHeader("X-Internal-Token", internalToken)
            .build();
  }

  public Optional<String> status(String appointmentId) {
    try {
      StatusResponse response =
          client
              .get()
              .uri("/internal/appointments/{id}/status", appointmentId)
              .retrieve()
              .body(StatusResponse.class);
      return response == null ? Optional.empty() : Optional.ofNullable(response.status());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() == 404) {
        return Optional.empty();
      }
      throw exception;
    }
  }

  public record StatusResponse(String status) {}
}
