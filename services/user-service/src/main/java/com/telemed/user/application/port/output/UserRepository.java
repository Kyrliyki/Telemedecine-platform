package com.telemed.user.application.port.output;

import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

  User save(User user);

  Optional<User> findById(UserId id);

  Optional<User> findByEmail(Email email);

  boolean existsByEmail(Email email);

  List<User> findAllByIds(Collection<UserId> ids);

  PageResult<User> findAll(String email, UserRole role, UserStatus status, int page, int size);
}
