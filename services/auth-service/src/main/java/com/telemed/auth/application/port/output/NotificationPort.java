package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.Email;
import java.util.Locale;
import java.util.Map;

public interface NotificationPort {

  void send(EmailMessage message);

  record EmailMessage(
      Email to,
      EmailTemplate template,
      Map<String, Object> variables,
      Locale locale,
      String correlationId) {}

  enum EmailTemplate {
    VERIFICATION,
    INVITATION,
    PASSWORD_RESET,
    OAUTH_LINK_CONFIRMATION
  }
}
