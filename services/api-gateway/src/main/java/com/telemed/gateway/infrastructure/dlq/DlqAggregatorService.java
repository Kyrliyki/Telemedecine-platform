package com.telemed.gateway.infrastructure.dlq;

import com.telemed.gateway.infrastructure.config.GatewayDlqProperties;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqOverview;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqPurgeResponse;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqQueueView;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqReplayResponse;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqServiceView;
import com.telemed.gateway.infrastructure.dlq.DlqViews.ServiceDlqPurgeResult;
import com.telemed.gateway.infrastructure.dlq.DlqViews.ServiceDlqReplayResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Aggregates service-local {@code /internal/dlq} endpoints into one admin API. */
@Service
public class DlqAggregatorService {

  private static final Logger log = LoggerFactory.getLogger(DlqAggregatorService.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(6);

  private final Map<String, ServiceClient> clients;

  public DlqAggregatorService(GatewayDlqProperties properties) {
    this.clients =
        properties.services().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    GatewayDlqProperties.Service::name,
                    service ->
                        new ServiceClient(
                            service.name(),
                            WebClient.builder()
                                .baseUrl(service.baseUrl())
                                .defaultHeader("X-Internal-Token", properties.internalToken())
                                .build())));
  }

  public Mono<DlqOverview> overview() {
    return Flux.fromIterable(clients.values())
        .flatMap(this::listService)
        .collectList()
        .map(services -> new DlqOverview(Instant.now(), services));
  }

  public Mono<DlqReplayResponse> replay(
      String service, String queueKey, int limit, boolean dryRun) {
    ServiceClient client = require(service);
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    return client
        .webClient()
        .post()
        .uri(
            uri ->
                uri.path("/internal/dlq/{key}/replay")
                    .queryParam("limit", safeLimit)
                    .queryParam("dryRun", dryRun)
                    .build(queueKey))
        .retrieve()
        .bodyToMono(ServiceDlqReplayResult.class)
        .timeout(TIMEOUT)
        .map(
            result ->
                new DlqReplayResponse(
                    service,
                    result.queue(),
                    result.replayed(),
                    result.failed(),
                    result.dryRun(),
                    result.candidates()))
        .onErrorMap(
            exception -> {
              log.warn("DLQ replay failed service={} queue={}", service, queueKey, exception);
              return new ResponseStatusException(
                  HttpStatus.BAD_GATEWAY, "DLQ replay failed in " + service, exception);
            });
  }

  public Mono<DlqPurgeResponse> purge(String service, String queueKey) {
    ServiceClient client = require(service);
    return client
        .webClient()
        .post()
        .uri(uri -> uri.path("/internal/dlq/{key}/purge").build(queueKey))
        .retrieve()
        .bodyToMono(ServiceDlqPurgeResult.class)
        .timeout(TIMEOUT)
        .map(result -> new DlqPurgeResponse(service, result.queue(), result.purged()))
        .onErrorMap(
            exception -> {
              log.warn("DLQ purge failed service={} queue={}", service, queueKey, exception);
              return new ResponseStatusException(
                  HttpStatus.BAD_GATEWAY, "DLQ purge failed in " + service, exception);
            });
  }

  private Mono<DlqServiceView> listService(ServiceClient client) {
    return client
        .webClient()
        .get()
        .uri("/internal/dlq")
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<DlqQueueView>>() {})
        .timeout(TIMEOUT)
        .map(queues -> new DlqServiceView(client.name(), true, null, queues))
        .onErrorResume(
            exception -> {
              log.warn("DLQ overview unavailable service={}", client.name(), exception);
              return Mono.just(
                  new DlqServiceView(client.name(), false, safeMessage(exception), List.of()));
            });
  }

  private ServiceClient require(String name) {
    ServiceClient client = clients.get(name);
    if (client == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown DLQ service: " + name);
    }
    return client;
  }

  private static String safeMessage(Throwable exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return exception.getClass().getSimpleName();
    }
    return message.length() > 240 ? message.substring(0, 240) : message;
  }

  private record ServiceClient(String name, WebClient webClient) {}
}
