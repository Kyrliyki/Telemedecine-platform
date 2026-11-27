package com.telemed.schedule.infrastructure.config;

import com.telemed.common.messaging.dlq.DlqQueueCatalog;
import com.telemed.common.messaging.dlq.DlqQueueDefinition;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

  public static final String AUTH_EXCHANGE = "auth.events";
  public static final String AUTH_DLX = "auth.events.dlx";
  public static final String AUTH_QUEUE = "schedule-service.auth.events";
  public static final String AUTH_DLQ = AUTH_QUEUE + ".dlq";
  public static final String SCHEDULE_EXCHANGE = "schedule.events";
  public static final String SCHEDULE_EVENTS_QUEUE = "schedule-service.events";
  public static final String APPOINTMENT_EXCHANGE = "appointment.events";
  public static final String APPOINTMENT_DLX = "appointment.events.dlx";
  public static final String APPOINTMENT_QUEUE = "schedule-service.appointment.events";
  public static final String APPOINTMENT_DLQ = APPOINTMENT_QUEUE + ".dlq";

  @Bean
  public DlqQueueCatalog dlqQueueCatalog() {
    return new DlqQueueCatalog(
        List.of(
            new DlqQueueDefinition(
                "auth",
                AUTH_DLQ,
                AUTH_EXCHANGE,
                "Auth events that schedule-service failed to consume"),
            new DlqQueueDefinition(
                "appointment",
                APPOINTMENT_DLQ,
                APPOINTMENT_EXCHANGE,
                "Appointment events that schedule-service failed to consume")));
  }

  @Bean
  public TopicExchange scheduleEventsExchange() {
    return ExchangeBuilder.topicExchange(SCHEDULE_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue scheduleEventsQueue() {
    return QueueBuilder.durable(SCHEDULE_EVENTS_QUEUE).build();
  }

  @Bean
  public Binding scheduleEventsBinding(
      Queue scheduleEventsQueue, TopicExchange scheduleEventsExchange) {
    return BindingBuilder.bind(scheduleEventsQueue).to(scheduleEventsExchange).with("schedule.#");
  }

  @Bean
  public TopicExchange appointmentEventsExchange() {
    return ExchangeBuilder.topicExchange(APPOINTMENT_EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange appointmentEventsDlx() {
    return ExchangeBuilder.topicExchange(APPOINTMENT_DLX).durable(true).build();
  }

  @Bean
  public Queue scheduleAppointmentQueue() {
    return QueueBuilder.durable(APPOINTMENT_QUEUE)
        .deadLetterExchange(APPOINTMENT_DLX)
        .deadLetterRoutingKey(APPOINTMENT_DLQ)
        .build();
  }

  @Bean
  public Queue scheduleAppointmentDeadLetterQueue() {
    return QueueBuilder.durable(APPOINTMENT_DLQ).build();
  }

  @Bean
  public Binding scheduleAppointmentBinding(
      Queue scheduleAppointmentQueue, TopicExchange appointmentEventsExchange) {
    return BindingBuilder.bind(scheduleAppointmentQueue)
        .to(appointmentEventsExchange)
        .with("appointment.appointment.#");
  }

  @Bean
  public Binding scheduleAppointmentDeadLetterBinding(
      Queue scheduleAppointmentDeadLetterQueue, TopicExchange appointmentEventsDlx) {
    return BindingBuilder.bind(scheduleAppointmentDeadLetterQueue)
        .to(appointmentEventsDlx)
        .with(APPOINTMENT_DLQ);
  }

  @Bean
  public TopicExchange authEventsExchange() {
    return ExchangeBuilder.topicExchange(AUTH_EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange authEventsDlx() {
    return ExchangeBuilder.topicExchange(AUTH_DLX).durable(true).build();
  }

  @Bean
  public Queue scheduleAuthQueue() {
    return QueueBuilder.durable(AUTH_QUEUE)
        .deadLetterExchange(AUTH_DLX)
        .deadLetterRoutingKey(AUTH_DLQ)
        .build();
  }

  @Bean
  public Queue scheduleAuthDeadLetterQueue() {
    return QueueBuilder.durable(AUTH_DLQ).build();
  }

  @Bean
  public Binding scheduleAuthBinding(Queue scheduleAuthQueue, TopicExchange authEventsExchange) {
    return BindingBuilder.bind(scheduleAuthQueue).to(authEventsExchange).with("auth.user.#");
  }

  @Bean
  public Binding scheduleAuthDeadLetterBinding(
      Queue scheduleAuthDeadLetterQueue, TopicExchange authEventsDlx) {
    return BindingBuilder.bind(scheduleAuthDeadLetterQueue).to(authEventsDlx).with(AUTH_DLQ);
  }
}
