package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.ProviderSubject;
import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.util.Optional;

public interface AuthUserRepository {
  Optional<AuthUser> findById(UserId id);

  Optional<AuthUser> findByEmail(Email email);

  Optional<AuthUser> findByExternalSubject(ProviderType provider, ProviderSubject subject);

  PageResult<AuthUser> findAll(String email, AuthStatus status, UserRole role, int page, int size);

  boolean existsByEmail(Email email);

  AuthUser save(AuthUser user);
}
