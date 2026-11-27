package com.telemed.user.application.port.input;

import com.telemed.user.application.dto.query.AdminUserQuery;
import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.dto.view.UserProfileView;

public interface ListUsersUseCase {

  PageResult<UserProfileView> findAll(AdminUserQuery query);
}
