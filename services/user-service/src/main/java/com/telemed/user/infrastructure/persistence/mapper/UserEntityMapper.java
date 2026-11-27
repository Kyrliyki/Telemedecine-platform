package com.telemed.user.infrastructure.persistence.mapper;

import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.infrastructure.persistence.entity.UserEntity;
import java.util.HashSet;
import org.springframework.stereotype.Component;

@Component
public class UserEntityMapper {

  public User toDomain(UserEntity entity) {
    return User.rehydrate(
        new UserId(entity.getId()),
        new Email(entity.getEmail()),
        entity.getFirstName(),
        entity.getLastName(),
        new HashSet<>(entity.getRoles()),
        entity.getStatus(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getLastSeenAt(),
        entity.getAuthUpdatedAt(),
        entity.getVersion());
  }

  public UserEntity toEntity(User user) {
    UserEntity entity = new UserEntity();
    entity.setId(user.getId().value());
    entity.setEmail(user.getEmail().value());
    entity.setFirstName(user.getFirstName());
    entity.setLastName(user.getLastName());
    entity.setRoles(new HashSet<>(user.getRoles()));
    entity.setStatus(user.getStatus());
    entity.setCreatedAt(user.getCreatedAt());
    entity.setUpdatedAt(user.getUpdatedAt());
    entity.setLastSeenAt(user.getLastSeenAt());
    entity.setAuthUpdatedAt(user.getAuthUpdatedAt());
    entity.setVersion(user.getVersion());
    return entity;
  }
}
