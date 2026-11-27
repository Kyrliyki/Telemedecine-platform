package com.telemed.common.messaging.converter;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration
public class JsonMessageConverterConfig {

  @Bean
  @ConditionalOnMissingBean(MessageConverter.class)
  public MessageConverter messageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
  }
}
