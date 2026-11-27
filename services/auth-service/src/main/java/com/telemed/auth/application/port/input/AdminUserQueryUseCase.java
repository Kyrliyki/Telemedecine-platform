package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.query.AdminUserQuery;
import com.telemed.auth.application.port.output.PageResult;
import com.telemed.auth.domain.model.AuthUser;
import java.util.UUID;

public interface AdminUserQueryUseCase {

  PageResult<AuthUser> findAll(AdminUserQuery query);

  AuthUser findById(UUID id);
}
