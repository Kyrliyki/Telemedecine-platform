package com.telemed.auth.application.port.output;

import java.util.List;

public interface PasswordPolicy {

  List<String> validate(String rawPassword, String email);
}
