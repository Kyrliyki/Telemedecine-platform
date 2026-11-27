package com.telemed.emr.infrastructure.config;

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

  public static final String APPOINTMENT_EXCHANGE = "appointment.events";
  public static final String EMR_EXCHANGE = "emr.events";
  public static final String EMR_EVENTS_QUEUE = "emr-service.events";
  public static final String APPOINTMENT_QUEUE = "emr-service.appointment.events";
  public static final String APPOINTMENT_DLQ = APPOINTMENT_QUEUE + ".dlq";

  @Bean
  public DlqQueueCatalog dlqQueueCatalog() {
    return new DlqQueueCatalog(
        List.of(
            new DlqQueueDefinition(
                "appointment",
                APPOINTMENT_DLQ,
                APPOINTMENT_EXCHANGE,
                "Appointment events that EMR failed to consume")));
  }

  @Bean
  public TopicExchange assignmentEventsExchange() {
    return ExchangeBuilder.topicExchange(EMR_EXCHANGE).durable(true).build();
  }

  @Bean
  public Queue emrEventsQueue() {
    return QueueBuilder.durable(EMR_EVENTS_QUEUE).build();
  }

  @Bean
  public Binding emrEventsBinding(Queue emrEventsQueue, TopicExchange assignmentEventsExchange) {
    return BindingBuilder.bind(emrEventsQueue).to(assignmentEventsExchange).with("emr.#");
  }

  @Bean
  public TopicExchange appointmentEventsExchange() {
    return ExchangeBuilder.topicExchange(APPOINTMENT_EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange emrDlx() {
    return ExchangeBuilder.topicExchange("emr.events.dlx").durable(true).build();
  }

  @Bean
  public Queue emrAppointmentQueue() {
    return QueueBuilder.durable(APPOINTMENT_QUEUE)
        .deadLetterExchange("emr.events.dlx")
        .deadLetterRoutingKey(APPOINTMENT_DLQ)
        .build();
  }

  @Bean
  public Queue emrAppointmentDlq() {
    return QueueBuilder.durable(APPOINTMENT_DLQ).build();
  }

  @Bean
  public Binding emrAppointmentBinding(
      Queue emrAppointmentQueue, TopicExchange appointmentEventsExchange) {
    return BindingBuilder.bind(emrAppointmentQueue)
        .to(appointmentEventsExchange)
        .with("appointment.appointment.#");
  }

  @Bean
  public Binding emrAppointmentDlqBinding(Queue emrAppointmentDlq, TopicExchange emrDlx) {
    return BindingBuilder.bind(emrAppointmentDlq).to(emrDlx).with(APPOINTMENT_DLQ);
  }
}
