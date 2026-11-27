package com.telemed.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@DiscriminatorValue("LOCAL")
public class LocalIdentityEntity extends UserIdentityEntity {

  @OneToOne(
      mappedBy = "identity",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private LocalCredentialEntity credential;
}
