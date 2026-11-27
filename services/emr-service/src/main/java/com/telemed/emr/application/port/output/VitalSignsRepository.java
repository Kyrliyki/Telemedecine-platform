package com.telemed.emr.application.port.output;

import com.telemed.emr.domain.model.VitalSigns;
import java.util.Optional;
import java.util.UUID;

public interface VitalSignsRepository {

  Optional<VitalSigns> findByRecordId(UUID recordId);

  VitalSigns save(VitalSigns signs);

  void deleteByRecordId(UUID recordId);
}
