package com.telemed.auth.infrastructure.persistence.mapper;

import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.InvitationId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.infrastructure.persistence.entity.InvitationEntity;
import org.springframework.stereotype.Component;

@Component
public class InvitationEntityMapper {

  public Invitation toDomain(InvitationEntity e) {
    return Invitation.rehydrate(
        new InvitationId(e.getId()),
        new UserId(e.getUserId()),
        new TokenHash(e.getTokenHash()),
        e.getCreatedAt(),
        e.getExpiresAt(),
        e.isAccepted(),
        e.getAcceptedAt());
  }

  public InvitationEntity toEntity(Invitation i) {
    InvitationEntity e = new InvitationEntity();
    e.setId(i.id().value());
    e.setUserId(i.userId().value());
    e.setTokenHash(i.tokenHash().value());
    e.setCreatedAt(i.createdAt());
    e.setExpiresAt(i.expiresAt());
    e.setAccepted(i.accepted());
    e.setAcceptedAt(i.acceptedAt());
    return e;
  }
}
