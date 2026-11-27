package com.telemed.common.security.config;

import com.telemed.common.security.filter.InternalNetworkFilter;
import com.telemed.common.security.filter.InternalServiceTokenFilter;
import com.telemed.common.security.filter.JwtAuthenticationFilter;
import com.telemed.common.security.filter.UserIdHeaderFilter;
import com.telemed.common.security.jwt.JwtAuthenticationConverter;
import com.telemed.common.security.jwt.JwtAutoConfiguration;
import com.telemed.common.security.jwt.TokenValidator;
import com.telemed.common.security.web.SecurityExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@AutoConfiguration(after = JwtAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "security",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "jakarta.servlet.Filter")
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JwtAuthenticationFilter jwtAuthenticationFilter(
      JwtDecoder decoder, TokenValidator validator, JwtAuthenticationConverter converter) {
    return new JwtAuthenticationFilter(decoder, validator, converter);
  }

  @Bean
  public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
      JwtAuthenticationFilter filter) {
    FilterRegistrationBean<JwtAuthenticationFilter> registration =
        new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  @ConditionalOnMissingBean
  public UserIdHeaderFilter userIdHeaderFilter() {
    return new UserIdHeaderFilter();
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityExceptionHandler securityExceptionHandler() {
    return new SecurityExceptionHandler();
  }

  @Bean
  @ConditionalOnProperty(prefix = "security.internal", name = "enabled", havingValue = "true")
  public FilterRegistrationBean<InternalNetworkFilter> internalNetworkFilter(
      SecurityProperties properties) {
    FilterRegistrationBean<InternalNetworkFilter> registration =
        new FilterRegistrationBean<>(
            new InternalNetworkFilter(properties.internal().allowedCidrs()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
    return registration;
  }

  @Bean
  @ConditionalOnProperty(prefix = "security.internal", name = "enabled", havingValue = "true")
  public FilterRegistrationBean<InternalServiceTokenFilter> internalServiceTokenFilter(
      SecurityProperties properties) {
    FilterRegistrationBean<InternalServiceTokenFilter> registration =
        new FilterRegistrationBean<>(
            new InternalServiceTokenFilter(
                properties.internal().token(), properties.internal().header()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
    return registration;
  }
}
