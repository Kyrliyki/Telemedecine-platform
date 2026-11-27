package com.telemed.emr.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.emr.domain.valueobject.RecordStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicalRecordTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void createsDraftAndUpdatesContent() {
    MedicalRecord record =
        MedicalRecord.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);

    record.update("Headache", "G43", "Migraine", "No red flags", "Rest", NOW.plusSeconds(10));

    assertThat(record.getStatus()).isEqualTo(RecordStatus.DRAFT);
    assertThat(record.getDiagnosisCode()).isEqualTo("G43");
    assertThat(record.getChiefComplaint()).isEqualTo("Headache");
    assertThat(record.getUpdatedAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void finalizesAndRejectsEditsUntilAmended() {
    MedicalRecord record =
        MedicalRecord.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), NOW);
    record.finalizeRecord(NOW.plusSeconds(5));

    assertThat(record.getStatus()).isEqualTo(RecordStatus.FINALIZED);
    assertThat(record.getFinalizedAt()).isEqualTo(NOW.plusSeconds(5));
    assertThatThrownBy(() -> record.update("x", null, null, null, null, NOW.plusSeconds(6)))
        .isInstanceOf(IllegalStateException.class);

    record.amend(NOW.plusSeconds(7));
    record.update(null, "G44", "Other headache", null, null, NOW.plusSeconds(8));
    assertThat(record.getStatus()).isEqualTo(RecordStatus.AMENDED);
    assertThat(record.getDiagnosisCode()).isEqualTo("G44");
  }

  @Test
  void deletionClearsClinicalText() {
    MedicalRecord record = MedicalRecord.draft(UUID.randomUUID(), UUID.randomUUID(), null, NOW);
    record.update("Pain", "R52", "Pain", "Notes", "Plan", NOW.plusSeconds(1));

    record.markDeleted(NOW.plusSeconds(2));

    assertThat(record.getStatus()).isEqualTo(RecordStatus.DELETED);
    assertThat(record.getNotes()).isNull();
    assertThat(record.getDiagnosisText()).isNull();
    assertThat(record.getDeletedAt()).isEqualTo(NOW.plusSeconds(2));
  }

  @Test
  void onlyAuthorCanEdit() {
    UUID doctor = UUID.randomUUID();
    MedicalRecord record = MedicalRecord.draft(UUID.randomUUID(), doctor, null, NOW);

    assertThat(record.canBeEditedBy(doctor)).isTrue();
    assertThat(record.canBeEditedBy(UUID.randomUUID())).isFalse();
  }
}
