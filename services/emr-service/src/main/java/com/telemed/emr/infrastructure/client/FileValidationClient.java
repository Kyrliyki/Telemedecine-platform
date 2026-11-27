package com.telemed.emr.infrastructure.client;

import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FileValidationClient {

  private final RestClient client;
  private final boolean enabled;

  public FileValidationClient(
      @Value("${emr.file-service.base-url:http://file-service:8086}") String baseUrl,
      @Value("${emr.file-service.internal-token:}") String internalToken,
      @Value("${emr.file-service.validation-enabled:false}") boolean enabled) {
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

  public void validate(UUID fileId, UUID patientId) {
    if (!enabled) {
      return;
    }
    client
        .get()
        .uri("/internal/files/{id}/metadata?ownerId={ownerId}", fileId, patientId)
        .retrieve()
        .toBodilessEntity();
  }
}
