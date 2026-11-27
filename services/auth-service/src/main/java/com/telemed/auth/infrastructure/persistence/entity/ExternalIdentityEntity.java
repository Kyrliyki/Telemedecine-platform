package com.telemed.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@DiscriminatorValue("EXTERNAL")
public class ExternalIdentityEntity extends UserIdentityEntity {

  @Column(name = "provider_email")
  private String providerEmail;

  @Column(name = "email_verified")
  private Boolean emailVerified;
}
