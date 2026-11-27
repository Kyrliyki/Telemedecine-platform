package com.telemed.auth.application.port.output;

import com.telemed.common.events.IntegrationEventEnvelope;

public interface OutboxAppender {
  void append(IntegrationEventEnvelope envelope);
}
