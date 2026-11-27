package com.telemed.gateway.application.port.input;

import com.telemed.gateway.domain.model.GatewayUser;
import reactor.core.publisher.Mono;

public interface GetCurrentUserUseCase {

  Mono<GatewayUser> currentUser();
}
