package com.telemed.user.domain.model;

import com.telemed.user.domain.exception.InvalidVerificationTransitionException;
import com.telemed.user.domain.valueobject.DoctorVerificationStatus;
import com.telemed.user.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class DoctorProfile {

  private final UserId userId;
  private final Instant createdAt;

  private String specialization;
  private String bio;
  private int yearsOfExperience;
  private String licenseNumber;
  private boolean acceptingPatients;
  private DoctorVerificationStatus verificationStatus;
  private Instant verifiedAt;
  private UUID verifiedBy;
  private String rejectionReason;
  private Instant updatedAt;
  private final long version;

  private DoctorProfile(
      UserId userId,
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      DoctorVerificationStatus verificationStatus,
      Instant verifiedAt,
      UUID verifiedBy,
      String rejectionReason,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.userId = Objects.requireNonNull(userId, "userId required");
    this.specialization = requireText(specialization, "specialization", 120);
    this.bio = bio == null ? "" : bio.trim();
    this.yearsOfExperience = validateExperience(yearsOfExperience);
    this.licenseNumber = licenseNumber == null ? "" : licenseNumber.trim();
    this.verificationStatus =
        verificationStatus == null ? DoctorVerificationStatus.PENDING : verificationStatus;
    this.acceptingPatients = acceptingPatients;
    this.verifiedAt = verifiedAt;
    this.verifiedBy = verifiedBy;
    this.rejectionReason = rejectionReason == null ? null : rejectionReason.trim();
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    this.version = version;
  }

  public static DoctorProfile create(
      UserId userId,
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      Instant now) {
    return create(
        userId,
        specialization,
        bio,
        yearsOfExperience,
        licenseNumber,
        acceptingPatients,
        DoctorVerificationStatus.PENDING,
        now);
  }

  public static DoctorProfile create(
      UserId userId,
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      DoctorVerificationStatus verificationStatus,
      Instant now) {
    return new DoctorProfile(
        userId,
        specialization,
        bio,
        yearsOfExperience,
        licenseNumber,
        acceptingPatients,
        verificationStatus,
        null,
        null,
        null,
        now,
        now,
        0L);
  }

  public static DoctorProfile rehydrate(
      UserId userId,
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return rehydrate(
        userId,
        specialization,
        bio,
        yearsOfExperience,
        licenseNumber,
        acceptingPatients,
        DoctorVerificationStatus.PENDING,
        null,
        null,
        null,
        createdAt,
        updatedAt,
        version);
  }

  public static DoctorProfile rehydrate(
      UserId userId,
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      DoctorVerificationStatus verificationStatus,
      Instant verifiedAt,
      UUID verifiedBy,
      String rejectionReason,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new DoctorProfile(
        userId,
        specialization,
        bio,
        yearsOfExperience,
        licenseNumber,
        acceptingPatients,
        verificationStatus,
        verifiedAt,
        verifiedBy,
        rejectionReason,
        createdAt,
        updatedAt,
        version);
  }

  public void update(
      String specialization,
      String bio,
      int yearsOfExperience,
      String licenseNumber,
      boolean acceptingPatients,
      Instant now) {
    requireNotBefore(now);
    this.specialization = requireText(specialization, "specialization", 120);
    this.bio = bio == null ? "" : bio.trim();
    this.yearsOfExperience = validateExperience(yearsOfExperience);
    this.licenseNumber = licenseNumber == null ? "" : licenseNumber.trim();
    this.acceptingPatients = acceptingPatients;
    this.updatedAt = now;
  }

  public void approve(UUID adminId, Instant now) {
    requireNotBefore(now);
    if (verificationStatus == DoctorVerificationStatus.VERIFIED) {
      throw new InvalidVerificationTransitionException("Doctor profile is already verified");
    }
    if (specialization == null || specialization.isBlank()) {
      throw new IllegalStateException("Doctor profile is incomplete");
    }
    if (licenseNumber == null || licenseNumber.isBlank()) {
      throw new IllegalStateException("License number is required before approval");
    }
    this.verificationStatus = DoctorVerificationStatus.VERIFIED;
    this.acceptingPatients = true;
    this.verifiedAt = now;
    this.verifiedBy = Objects.requireNonNull(adminId, "adminId required");
    this.rejectionReason = null;
    this.updatedAt = now;
  }

  public void reject(UUID adminId, String reason, Instant now) {
    requireNotBefore(now);
    if (verificationStatus == DoctorVerificationStatus.REJECTED) {
      throw new InvalidVerificationTransitionException("Doctor profile is already rejected");
    }
    this.verificationStatus = DoctorVerificationStatus.REJECTED;
    this.verifiedAt = null;
    this.verifiedBy = Objects.requireNonNull(adminId, "adminId required");
    this.rejectionReason =
        reason == null || reason.isBlank() ? "Verification rejected" : reason.trim();
    this.acceptingPatients = false;
    this.updatedAt = now;
  }

  public void submitForVerification(Instant now) {
    requireNotBefore(now);
    this.verificationStatus = DoctorVerificationStatus.PENDING;
    this.verifiedAt = null;
    this.verifiedBy = null;
    this.rejectionReason = null;
    this.updatedAt = now;
  }

  public boolean isVerified() {
    return verificationStatus == DoctorVerificationStatus.VERIFIED;
  }

  public boolean isPubliclyVisible() {
    return isVerified() && acceptingPatients;
  }

  public UserId getUserId() {
    return userId;
  }

  public String getSpecialization() {
    return specialization;
  }

  public String getBio() {
    return bio;
  }

  public int getYearsOfExperience() {
    return yearsOfExperience;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public boolean isAcceptingPatients() {
    return acceptingPatients;
  }

  public DoctorVerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public UUID getVerifiedBy() {
    return verifiedBy;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  private void requireNotBefore(Instant now) {
    Objects.requireNonNull(now, "now required");
    if (now.isBefore(updatedAt)) {
      throw new IllegalArgumentException("Update time cannot be before updatedAt");
    }
  }

  private static String requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(field + " is too long");
    }
    return trimmed;
  }

  private static int validateExperience(int value) {
    if (value < 0 || value > 80) {
      throw new IllegalArgumentException("yearsOfExperience must be between 0 and 80");
    }
    return value;
  }
}
