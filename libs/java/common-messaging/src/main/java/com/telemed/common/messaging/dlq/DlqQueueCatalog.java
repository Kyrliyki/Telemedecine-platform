package com.telemed.common.messaging.dlq;

import java.util.List;
import java.util.Optional;

/** Catalog of DLQs exposed by one service. */
public record DlqQueueCatalog(List<DlqQueueDefinition> queues) {

  public DlqQueueCatalog {
    queues = queues == null ? List.of() : List.copyOf(queues);
  }

  public Optional<DlqQueueDefinition> find(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return queues.stream().filter(queue -> queue.key().equalsIgnoreCase(key)).findFirst();
  }
}
