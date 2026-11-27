package com.telemed.common.observability.config;

import com.telemed.common.observability.filter.CorrelationIdFilter;
import com.telemed.common.observability.filter.RequestIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet-specific request observability filters. Reactive applications (WebFlux) must not
 * load this configuration because Jakarta Servlet is not on their classpath.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class ServletObservabilityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }
}
