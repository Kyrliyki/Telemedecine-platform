package com.telemed.user.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_profiles")
public class DoctorProfileEntity {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Column(nullable = false, length = 120)
  private String specialization;

  @Column(nullable = false, columnDefinition = "text")
  private String bio;

  @Column(name = "years_experience", nullable = false)
  private int yearsExperience;

  @Column(name = "license_number", nullable = false, length = 64)
  private String licenseNumber;

  @Column(name = "accepting_patients", nullable = false)
  private boolean acceptingPatients;

  @Column(name = "verification_status", nullable = false, length = 32)
  private String verificationStatus = "PENDING";

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "verified_by")
  private UUID verifiedBy;

  @Column(name = "rejection_reason", length = 512)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @jakarta.persistence.Version
  @Column(nullable = false)
  private long version;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getSpecialization() {
    return specialization;
  }

  public void setSpecialization(String specialization) {
    this.specialization = specialization;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public int getYearsExperience() {
    return yearsExperience;
  }

  public void setYearsExperience(int yearsExperience) {
    this.yearsExperience = yearsExperience;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public boolean isAcceptingPatients() {
    return acceptingPatients;
  }

  public void setAcceptingPatients(boolean acceptingPatients) {
    this.acceptingPatients = acceptingPatients;
  }

  public String getVerificationStatus() {
    return verificationStatus;
  }

  public void setVerificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }

  public void setVerifiedAt(Instant verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  public UUID getVerifiedBy() {
    return verifiedBy;
  }

  public void setVerifiedBy(UUID verifiedBy) {
    this.verifiedBy = verifiedBy;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
