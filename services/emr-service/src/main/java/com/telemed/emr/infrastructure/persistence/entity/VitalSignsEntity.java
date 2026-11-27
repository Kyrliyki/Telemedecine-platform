package com.telemed.emr.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vital_signs")
public class VitalSignsEntity {

  @Id private UUID id;

  @Column(name = "record_id", nullable = false)
  private UUID recordId;

  @Column(name = "height_cm")
  private BigDecimal heightCm;

  @Column(name = "weight_kg")
  private BigDecimal weightKg;

  private Integer systolic;

  private Integer diastolic;

  private Integer pulse;

  @Column(name = "temperature_c")
  private BigDecimal temperatureC;

  private Integer spo2;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getRecordId() {
    return recordId;
  }

  public void setRecordId(UUID recordId) {
    this.recordId = recordId;
  }

  public BigDecimal getHeightCm() {
    return heightCm;
  }

  public void setHeightCm(BigDecimal heightCm) {
    this.heightCm = heightCm;
  }

  public BigDecimal getWeightKg() {
    return weightKg;
  }

  public void setWeightKg(BigDecimal weightKg) {
    this.weightKg = weightKg;
  }

  public Integer getSystolic() {
    return systolic;
  }

  public void setSystolic(Integer systolic) {
    this.systolic = systolic;
  }

  public Integer getDiastolic() {
    return diastolic;
  }

  public void setDiastolic(Integer diastolic) {
    this.diastolic = diastolic;
  }

  public Integer getPulse() {
    return pulse;
  }

  public void setPulse(Integer pulse) {
    this.pulse = pulse;
  }

  public BigDecimal getTemperatureC() {
    return temperatureC;
  }

  public void setTemperatureC(BigDecimal temperatureC) {
    this.temperatureC = temperatureC;
  }

  public Integer getSpo2() {
    return spo2;
  }

  public void setSpo2(Integer spo2) {
    this.spo2 = spo2;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }
}
