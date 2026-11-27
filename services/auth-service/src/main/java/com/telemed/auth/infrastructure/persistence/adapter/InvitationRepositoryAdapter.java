package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.InvitationRepository;
import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.infrastructure.persistence.mapper.InvitationEntityMapper;
import com.telemed.auth.infrastructure.persistence.repository.JpaInvitationRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvitationRepositoryAdapter implements InvitationRepository {

  private final JpaInvitationRepository jpa;
  private final InvitationEntityMapper mapper;

  @Override
  public Invitation save(Invitation invitation) {
    var entity = mapper.toEntity(invitation);
    jpa.findById(entity.getId()).ifPresent(existing -> entity.setVersion(existing.getVersion()));
    return mapper.toDomain(jpa.save(entity));
  }

  @Override
  public Optional<Invitation> findByTokenHash(TokenHash tokenHash) {
    return jpa.findByTokenHashForUpdate(tokenHash.value()).map(mapper::toDomain);
  }
}
