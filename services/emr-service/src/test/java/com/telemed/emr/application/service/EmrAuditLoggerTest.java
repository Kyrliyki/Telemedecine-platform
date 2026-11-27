package com.telemed.emr.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.emr.infrastructure.persistence.EmrAuditWriter;
import com.telemed.emr.infrastructure.persistence.entity.EmrAuditLogEntity;
import com.telemed.emr.infrastructure.persistence.repository.JpaEmrAuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EmrAuditLoggerTest {

  @Mock private JpaEmrAuditLogRepository repository;

  private EmrAuditLogger logger() {
    return new EmrAuditLogger(
        repository, new EmrAuditWriter(repository), JsonMapper.builder().build());
  }

  @Test
  void recordsCreatedAndAccessDeniedWithSafeDetails() {
    UUID recordId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID doctorId = UUID.randomUUID();
    EmrAuditLogger audit = logger();

    audit.recordCreated(recordId, patientId, doctorId);
    audit.accessDenied(recordId, doctorId, "view");

    ArgumentCaptor<EmrAuditLogEntity> captor = ArgumentCaptor.forClass(EmrAuditLogEntity.class);
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertThat(captor.getAllValues().get(0).getAction()).isEqualTo("RECORD_CREATED");
    assertThat(captor.getAllValues().get(0).getActorId()).isEqualTo(doctorId);
    assertThat(captor.getAllValues().get(1).getAction()).isEqualTo("ACCESS_DENIED");
    assertThat(captor.getAllValues().get(1).getDetails()).contains("view");
  }

  @Test
  void readsAuditRowsForRecord() {
    UUID recordId = UUID.randomUUID();
    EmrAuditLogEntity row = new EmrAuditLogEntity();
    row.setId(UUID.randomUUID());
    row.setRecordId(recordId);
    row.setAction("RECORD_VIEWED");
    row.setCreatedAt(Instant.now());
    when(repository.findAllByRecordIdOrderByCreatedAtAsc(recordId)).thenReturn(List.of(row));

    var result = logger().forRecord(recordId);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().action()).isEqualTo("RECORD_VIEWED");
  }
}
