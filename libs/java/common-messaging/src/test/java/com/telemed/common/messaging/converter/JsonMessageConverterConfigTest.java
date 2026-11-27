package com.telemed.common.messaging.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class JsonMessageConverterConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JsonMessageConverterConfig.class))
          .withBean(JsonMapper.class, () -> JsonMapper.builder().build());

  @Test
  void createsJacksonConverterByDefault() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(MessageConverter.class);
          assertThat(context.getBean(MessageConverter.class))
              .isInstanceOf(JacksonJsonMessageConverter.class);
        });
  }

  @Test
  void backsOffWhenServiceDefinesConverter() {
    runner
        .withBean("customConverter", MessageConverter.class, SimpleMessageConverter::new)
        .run(
            context ->
                assertThat(context.getBean(MessageConverter.class))
                    .isInstanceOf(SimpleMessageConverter.class));
  }
}
