package com.telemed.auth.infrastructure.bootstrap;

import com.telemed.auth.infrastructure.config.BootstrapAdminProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

  private final BootstrapAdminProperties properties;
  private final BootstrapAdminService service;

  @Override
  public void run(ApplicationArguments args) {
    if (properties.email() == null
        || properties.email().isBlank()
        || properties.password() == null
        || properties.password().isBlank()) {
      throw new IllegalStateException(
          "auth.bootstrap-admin.enabled=true requires email and password");
    }
    boolean created = service.createIfMissing(properties.email(), properties.password());
    if (!created) {
      log.info("Bootstrap admin step skipped: user already present");
    }
  }
}
