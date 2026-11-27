package com.telemed.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.user.domain.exception.InvalidVerificationTransitionException;
import com.telemed.user.domain.valueobject.DoctorVerificationStatus;
import com.telemed.user.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoctorProfileTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void normalizesProfileValues() {
    DoctorProfile profile =
        DoctorProfile.create(
            UserId.newId(), "  Cardiology ", "  Heart doctor ", 12, "  LIC-1 ", true, NOW);

    assertThat(profile.getSpecialization()).isEqualTo("Cardiology");
    assertThat(profile.getBio()).isEqualTo("Heart doctor");
    assertThat(profile.getLicenseNumber()).isEqualTo("LIC-1");
    assertThat(profile.isAcceptingPatients()).isTrue();
  }

  @Test
  void rejectsBlankSpecialization() {
    assertThatThrownBy(() -> DoctorProfile.create(UserId.newId(), " ", "", 0, "", true, NOW))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("specialization");
  }

  @Test
  void rejectsInvalidExperience() {
    assertThatThrownBy(
            () -> DoctorProfile.create(UserId.newId(), "Cardiology", "", 81, "", true, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updatesProfileAndTimestamp() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "", true, NOW);

    profile.update("Neurology", "Neuro", 5, "LIC-2", false, NOW.plusSeconds(30));

    assertThat(profile.getSpecialization()).isEqualTo("Neurology");
    assertThat(profile.getYearsOfExperience()).isEqualTo(5);
    assertThat(profile.isAcceptingPatients()).isFalse();
    assertThat(profile.getUpdatedAt()).isEqualTo(NOW.plusSeconds(30));
  }

  @Test
  void approvalMakesProfilePubliclyVisible() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    assertThat(profile.isPubliclyVisible()).isFalse();

    java.util.UUID adminId = java.util.UUID.randomUUID();
    profile.approve(adminId, NOW.plusSeconds(10));

    assertThat(profile.getVerificationStatus().name()).isEqualTo("VERIFIED");
    assertThat(profile.getVerifiedBy()).isEqualTo(adminId);
    assertThat(profile.isPubliclyVisible()).isTrue();
  }

  @Test
  void rejectionHidesProfileAndStoresReason() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);

    profile.reject(java.util.UUID.randomUUID(), "bad license", NOW.plusSeconds(10));

    assertThat(profile.getVerificationStatus().name()).isEqualTo("REJECTED");
    assertThat(profile.getRejectionReason()).isEqualTo("bad license");
    assertThat(profile.isAcceptingPatients()).isFalse();
    assertThat(profile.isPubliclyVisible()).isFalse();
  }

  @Test
  void verifiedProfileCannotBeApprovedAgain() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    profile.approve(UUID.randomUUID(), NOW.plusSeconds(10));

    assertThatThrownBy(() -> profile.approve(UUID.randomUUID(), NOW.plusSeconds(20)))
        .isInstanceOf(InvalidVerificationTransitionException.class)
        .hasMessageContaining("already verified");
  }

  @Test
  void verifiedProfileCanBeRejectedAsRevocation() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    UUID verifier = UUID.randomUUID();
    profile.approve(verifier, NOW.plusSeconds(10));
    UUID reviewer = UUID.randomUUID();

    profile.reject(reviewer, "license revoked", NOW.plusSeconds(20));

    assertThat(profile.getVerificationStatus()).isEqualTo(DoctorVerificationStatus.REJECTED);
    assertThat(profile.getVerifiedAt()).isNull();
    assertThat(profile.getVerifiedBy()).isEqualTo(reviewer);
    assertThat(profile.getRejectionReason()).isEqualTo("license revoked");
    assertThat(profile.isAcceptingPatients()).isFalse();
  }

  @Test
  void rejectedProfileCannotBeRejectedAgain() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    profile.reject(UUID.randomUUID(), "bad license", NOW.plusSeconds(10));

    assertThatThrownBy(() -> profile.reject(UUID.randomUUID(), "again", NOW.plusSeconds(20)))
        .isInstanceOf(InvalidVerificationTransitionException.class)
        .hasMessageContaining("already rejected");
  }

  @Test
  void ownerUpdateSendsVerifiedProfileBackToVerification() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    profile.approve(UUID.randomUUID(), NOW.plusSeconds(10));

    profile.update("Cardiology", "Updated", 2, "LIC-1", true, NOW.plusSeconds(20));
    profile.submitForVerification(NOW.plusSeconds(20));

    assertThat(profile.getVerificationStatus()).isEqualTo(DoctorVerificationStatus.PENDING);
    assertThat(profile.getVerifiedAt()).isNull();
    assertThat(profile.getVerifiedBy()).isNull();
    assertThat(profile.getRejectionReason()).isNull();
  }

  @Test
  void rejectedProfileCanBeApprovedByAdmin() {
    DoctorProfile profile =
        DoctorProfile.create(UserId.newId(), "Cardiology", "", 1, "LIC-1", true, NOW);
    profile.reject(UUID.randomUUID(), "bad license", NOW.plusSeconds(10));

    profile.approve(UUID.randomUUID(), NOW.plusSeconds(20));

    assertThat(profile.getVerificationStatus()).isEqualTo(DoctorVerificationStatus.VERIFIED);
    assertThat(profile.getRejectionReason()).isNull();
    assertThat(profile.isAcceptingPatients()).isTrue();
    assertThat(profile.isPubliclyVisible()).isTrue();
  }
}
