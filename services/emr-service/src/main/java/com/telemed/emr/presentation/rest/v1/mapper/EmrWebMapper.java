package com.telemed.emr.presentation.rest.v1.mapper;

import com.telemed.emr.application.dto.command.AddAttachmentCommand;
import com.telemed.emr.application.dto.command.CreateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.ReplacePrescriptionsCommand;
import com.telemed.emr.application.dto.command.UpdateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.UpsertVitalSignsCommand;
import com.telemed.emr.application.dto.view.AttachmentView;
import com.telemed.emr.application.dto.view.MedicalRecordView;
import com.telemed.emr.application.dto.view.PageResult;
import com.telemed.emr.application.dto.view.PrescriptionView;
import com.telemed.emr.application.dto.view.VitalSignsView;
import com.telemed.emr.presentation.rest.v1.dto.request.AddAttachmentRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.CreateMedicalRecordRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.ReplacePrescriptionsRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.UpdateMedicalRecordRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.UpsertVitalSignsRequest;
import com.telemed.emr.presentation.rest.v1.dto.response.AttachmentResponse;
import com.telemed.emr.presentation.rest.v1.dto.response.MedicalRecordPageResponse;
import com.telemed.emr.presentation.rest.v1.dto.response.MedicalRecordResponse;
import com.telemed.emr.presentation.rest.v1.dto.response.PrescriptionResponse;
import com.telemed.emr.presentation.rest.v1.dto.response.VitalSignsResponse;
import org.springframework.stereotype.Component;

@Component
public class EmrWebMapper {

  public MedicalRecordResponse toResponse(MedicalRecordView view) {
    return new MedicalRecordResponse(
        view.id(),
        view.patientId(),
        view.doctorId(),
        view.appointmentId(),
        view.status(),
        view.chiefComplaint(),
        view.diagnosisCode(),
        view.diagnosisText(),
        view.notes(),
        view.treatmentPlan(),
        view.createdAt(),
        view.updatedAt(),
        view.finalizedAt(),
        view.version(),
        view.prescriptions().stream().map(this::toResponse).toList(),
        view.vitalSigns() == null ? null : toResponse(view.vitalSigns()),
        view.attachments().stream().map(this::toResponse).toList());
  }

  public PrescriptionResponse toResponse(PrescriptionView view) {
    return new PrescriptionResponse(
        view.id(),
        view.medicationName(),
        view.dosage(),
        view.frequency(),
        view.durationDays(),
        view.instructions(),
        view.createdAt());
  }

  public VitalSignsResponse toResponse(VitalSignsView view) {
    return new VitalSignsResponse(
        view.id(),
        view.heightCm(),
        view.weightKg(),
        view.systolic(),
        view.diastolic(),
        view.pulse(),
        view.temperatureC(),
        view.spo2(),
        view.recordedAt());
  }

  public AttachmentResponse toResponse(AttachmentView view) {
    return new AttachmentResponse(view.id(), view.fileId(), view.description(), view.createdAt());
  }

  public MedicalRecordPageResponse toPageResponse(PageResult<MedicalRecordView> page) {
    return new MedicalRecordPageResponse(
        page.content().stream().map(this::toResponse).toList(),
        page.totalElements(),
        page.page(),
        page.size(),
        page.totalPages());
  }

  public CreateMedicalRecordCommand toCommand(CreateMedicalRecordRequest request) {
    return new CreateMedicalRecordCommand(
        request.patientId(),
        request.appointmentId(),
        request.chiefComplaint(),
        request.diagnosisCode(),
        request.diagnosisText(),
        request.notes(),
        request.treatmentPlan());
  }

  public UpdateMedicalRecordCommand toCommand(UpdateMedicalRecordRequest request) {
    return new UpdateMedicalRecordCommand(
        request.chiefComplaint(),
        request.diagnosisCode(),
        request.diagnosisText(),
        request.notes(),
        request.treatmentPlan());
  }

  public ReplacePrescriptionsCommand toCommand(ReplacePrescriptionsRequest request) {
    return new ReplacePrescriptionsCommand(
        (request.entries() == null
                ? java.util.List.<ReplacePrescriptionsRequest.Entry>of()
                : request.entries())
            .stream()
                .map(
                    entry ->
                        new ReplacePrescriptionsCommand.Entry(
                            entry.medicationName(),
                            entry.dosage(),
                            entry.frequency(),
                            entry.durationDays(),
                            entry.instructions()))
                .toList());
  }

  public UpsertVitalSignsCommand toCommand(UpsertVitalSignsRequest request) {
    return new UpsertVitalSignsCommand(
        request.heightCm(),
        request.weightKg(),
        request.systolic(),
        request.diastolic(),
        request.pulse(),
        request.temperatureC(),
        request.spo2());
  }

  public AddAttachmentCommand toCommand(AddAttachmentRequest request) {
    return new AddAttachmentCommand(request.fileId(), request.description());
  }
}
