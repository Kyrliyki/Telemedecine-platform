package com.telemed.schedule.presentation.rest.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.schedule.application.dto.command.ReserveSlotCommand;
import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.domain.valueobject.SlotStatus;
import com.telemed.schedule.presentation.rest.v1.dto.request.ReserveSlotRequest;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalSlotControllerTest {

  @Mock private SlotReservationService service;

  @Test
  void passesPatientIdToHoldCommand() {
    UUID doctorId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID appointmentId = UUID.randomUUID();
    Instant startsAt = Instant.parse("2026-01-05T09:00:00Z");
    SlotView expected =
        new SlotView(
            UUID.randomUUID(),
            doctorId,
            startsAt,
            startsAt.plusSeconds(1800),
            SlotStatus.HELD,
            appointmentId,
            startsAt.plusSeconds(900),
            "STANDARD",
            "Room 1");
    when(service.hold(any())).thenReturn(expected);

    InternalSlotController controller =
        new InternalSlotController(service, new ScheduleWebMapper());
    var response =
        controller.hold(new ReserveSlotRequest(doctorId, startsAt, appointmentId, patientId));

    ArgumentCaptor<ReserveSlotCommand> captor = ArgumentCaptor.forClass(ReserveSlotCommand.class);
    verify(service).hold(captor.capture());
    assertThat(captor.getValue().patientId()).isEqualTo(patientId);
    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody().status()).isEqualTo("HELD");
  }
}
