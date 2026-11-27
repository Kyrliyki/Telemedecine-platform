package com.telemed.gateway.application.port.output;

import com.telemed.gateway.domain.model.GatewayUser;
import reactor.core.publisher.Mono;

public interface CurrentUserReader {

  Mono<GatewayUser> read();
}
