package com.telemed.schedule.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.infrastructure.persistence.adapter.ProcessedEventRepositoryAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

class AppointmentEventConsumerTest {

  @Test
  void rejectsEventWithoutEventType() {
    AppointmentEventConsumer consumer =
        new AppointmentEventConsumer(
            JsonMapper.builder().build(),
            mock(SlotReservationService.class),
            mock(ProcessedEventRepositoryAdapter.class),
            mock(Clock.class));

    Message message = new Message(new byte[0], new MessageProperties());

    assertThatThrownBy(() -> consumer.handle(message))
        .isInstanceOf(AmqpRejectAndDontRequeueException.class);
  }
}
