package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.PageResult;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.*;
import com.telemed.auth.infrastructure.persistence.entity.AuthUserEntity;
import com.telemed.auth.infrastructure.persistence.mapper.AuthUserEntityMapper;
import com.telemed.auth.infrastructure.persistence.repository.JpaAuthUserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthUserRepositoryAdapter implements AuthUserRepository {

  private final JpaAuthUserRepository jpa;
  private final AuthUserEntityMapper mapper;

  @Override
  public Optional<AuthUser> findById(UserId id) {
    return jpa.findById(id.value()).map(mapper::toDomain);
  }

  @Override
  public Optional<AuthUser> findByEmail(Email email) {
    return jpa.findByEmail(email.value()).map(mapper::toDomain);
  }

  @Override
  public Optional<AuthUser> findByExternalSubject(ProviderType provider, ProviderSubject subject) {
    return jpa.findByExternalSubject(provider, subject.value()).map(mapper::toDomain);
  }

  @Override
  public PageResult<AuthUser> findAll(
      String email, AuthStatus status, UserRole role, int page, int size) {
    Specification<AuthUserEntity> specification =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (email != null && !email.isBlank()) {
            predicates.add(
                cb.like(cb.lower(root.get("email")), "%" + email.toLowerCase(Locale.ROOT) + "%"));
          }
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (role != null) {
            predicates.add(cb.isMember(role, root.get("roles")));
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

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }

  @Override
  public AuthUser save(AuthUser user) {
    AuthUserEntity entity = mapper.toEntity(user);
    jpa.findById(entity.getId()).ifPresent(existing -> entity.setVersion(existing.getVersion()));
    return mapper.toDomain(jpa.save(entity));
  }
}
