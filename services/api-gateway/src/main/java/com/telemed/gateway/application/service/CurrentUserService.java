package com.telemed.gateway.application.service;

import com.telemed.gateway.application.port.input.GetCurrentUserUseCase;
import com.telemed.gateway.application.port.output.CurrentUserReader;
import com.telemed.gateway.domain.model.GatewayUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CurrentUserService implements GetCurrentUserUseCase {

  private final CurrentUserReader currentUserReader;

  @Override
  public Mono<GatewayUser> currentUser() {
    return currentUserReader.read();
  }
}
