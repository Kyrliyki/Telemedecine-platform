package com.telemed.emr.domain.valueobject;

public enum RecordStatus {
  DRAFT,
  FINALIZED,
  AMENDED,
  DELETED;

  public boolean isEditable() {
    return this == DRAFT || this == AMENDED;
  }

  public boolean isVisible() {
    return this != DELETED;
  }
}
