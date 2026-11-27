package com.telemed.auth.infrastructure.bootstrap;

import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.application.port.output.PasswordPolicy;
import com.telemed.auth.application.service.OutboxEventWriter;
import com.telemed.auth.domain.model.Actor;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserRole;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapAdminService {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminService.class);

  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final Clock clock;
  private final OutboxEventWriter outboxEventWriter;

  @Transactional
  public boolean createIfMissing(String email, String rawPassword) {
    Email normalizedEmail = new Email(email);
    if (authUserRepository.existsByEmail(normalizedEmail)) {
      log.info("Bootstrap admin already exists: {}", normalizedEmail.value());
      return false;
    }

    var violations = passwordPolicy.validate(rawPassword, normalizedEmail.value());
    if (!violations.isEmpty()) {
      throw new IllegalStateException("Bootstrap admin password violates policy: " + violations);
    }

    var now = clock.now();
    AuthUser user =
        AuthUser.registerWithPassword(normalizedEmail, passwordEncoder.hash(rawPassword), now);
    user.verifyEmail(now);
    user.activate(now);
    user.assignRole(UserRole.ADMIN, Actor.systemActor(), now);

    authUserRepository.save(user);
    outboxEventWriter.write(user, null);
    log.info("Bootstrap admin created: {}", normalizedEmail.value());
    return true;
  }
}
