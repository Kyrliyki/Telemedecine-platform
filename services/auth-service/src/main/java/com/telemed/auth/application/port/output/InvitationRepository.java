package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.TokenHash;
import java.util.Optional;

public interface InvitationRepository {

  Invitation save(Invitation invitation);

  Optional<Invitation> findByTokenHash(TokenHash tokenHash);
}
