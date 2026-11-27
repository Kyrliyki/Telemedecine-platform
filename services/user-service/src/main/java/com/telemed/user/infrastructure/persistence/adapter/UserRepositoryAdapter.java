package com.telemed.user.infrastructure.persistence.adapter;

import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.persistence.entity.UserEntity;
import com.telemed.user.infrastructure.persistence.mapper.UserEntityMapper;
import com.telemed.user.infrastructure.persistence.repository.JpaUserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryAdapter implements UserRepository {

  private final JpaUserRepository jpa;
  private final UserEntityMapper mapper;

  public UserRepositoryAdapter(JpaUserRepository jpa, UserEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public User save(User user) {
    return mapper.toDomain(jpa.save(mapper.toEntity(user)));
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public Optional<User> findByEmail(Email email) {
    return jpa.findByEmail(email.value()).map(mapper::toDomain);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }

  @Override
  public List<User> findAllByIds(Collection<UserId> ids) {
    if (ids == null || ids.isEmpty()) {
      return List.of();
    }
    List<java.util.UUID> values = ids.stream().map(UserId::value).toList();
    return jpa.findAllById(values).stream().map(mapper::toDomain).toList();
  }

  @Override
  public PageResult<User> findAll(
      String email, UserRole role, UserStatus status, int page, int size) {
    Specification<UserEntity> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (email != null && !email.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("email")),
                    "%" + email.trim().toLowerCase(Locale.ROOT) + "%"));
          }
          if (role != null) {
            predicates.add(cb.isMember(role, root.get("roles")));
          }
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          return cb.and(predicates.toArray(Predicate[]::new));
        };

    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    var result = jpa.findAll(specification, pageable);

    return new PageResult<>(
        result.getContent().stream().map(mapper::toDomain).toList(),
        result.getTotalElements(),
        result.getNumber(),
        result.getSize());
  }
}
