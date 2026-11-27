package com.telemed.common.events;

public interface EventPublisher {

    void publish(IntegrationEventEnvelope event);
}