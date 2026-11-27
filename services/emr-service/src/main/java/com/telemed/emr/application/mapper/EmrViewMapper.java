package com.telemed.emr.application.mapper;

import com.telemed.emr.application.dto.view.AttachmentView;
import com.telemed.emr.application.dto.view.MedicalRecordView;
import com.telemed.emr.application.dto.view.PrescriptionView;
import com.telemed.emr.application.dto.view.VitalSignsView;
import com.telemed.emr.domain.model.MedicalAttachment;
import com.telemed.emr.domain.model.MedicalRecord;
import com.telemed.emr.domain.model.Prescription;
import com.telemed.emr.domain.model.VitalSigns;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmrViewMapper {

  public MedicalRecordView toView(
      MedicalRecord record,
      List<Prescription> prescriptions,
      VitalSigns vitalSigns,
      List<MedicalAttachment> attachments) {
    return new MedicalRecordView(
        record.getId(),
        record.getPatientId(),
        record.getDoctorId(),
        record.getAppointmentId(),
        record.getStatus().name(),
        record.getChiefComplaint(),
        record.getDiagnosisCode(),
        record.getDiagnosisText(),
        record.getNotes(),
        record.getTreatmentPlan(),
        record.getCreatedAt(),
        record.getUpdatedAt(),
        record.getFinalizedAt(),
        record.getVersion(),
        prescriptions.stream().map(this::toView).toList(),
        vitalSigns == null ? null : toView(vitalSigns),
        attachments.stream().map(this::toView).toList());
  }

  public PrescriptionView toView(Prescription prescription) {
    return new PrescriptionView(
        prescription.id(),
        prescription.medicationName(),
        prescription.dosage(),
        prescription.frequency(),
        prescription.durationDays(),
        prescription.instructions(),
        prescription.createdAt());
  }

  public VitalSignsView toView(VitalSigns signs) {
    return new VitalSignsView(
        signs.id(),
        signs.heightCm(),
        signs.weightKg(),
        signs.systolic(),
        signs.diastolic(),
        signs.pulse(),
        signs.temperatureC(),
        signs.spo2(),
        signs.recordedAt());
  }

  public AttachmentView toView(MedicalAttachment attachment) {
    return new AttachmentView(
        attachment.id(), attachment.fileId(), attachment.description(), attachment.createdAt());
  }
}
