package com.telemed.schedule.presentation.rest.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.service.AvailabilityService;
import com.telemed.schedule.domain.valueobject.SlotStatus;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerTest {

  private static final UUID DOCTOR = UUID.randomUUID();
  private static final LocalDate FROM = LocalDate.of(2026, 1, 5);

  @Mock private AvailabilityService service;

  private AvailabilityController controller;

  @BeforeEach
  void setUp() {
    controller = new AvailabilityController(service, new ScheduleWebMapper());
  }

  @Test
  void returnsEtagAndThen304ForSameSlots() {
    when(service.availability(DOCTOR, FROM, FROM, 0)).thenReturn(List.of(slot()));

    var first = controller.availability(DOCTOR, FROM, FROM, 0, null);
    String etag = first.getHeaders().getETag();
    var second = controller.availability(DOCTOR, FROM, FROM, 0, etag);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(etag).isNotBlank();
    assertThat(first.getHeaders().getCacheControl()).contains("private").contains("max-age=15");
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
    assertThat(second.getBody()).isNull();
  }

  private static SlotView slot() {
    Instant startsAt = Instant.parse("2026-01-05T09:00:00Z");
    return new SlotView(
        UUID.randomUUID(),
        DOCTOR,
        startsAt,
        startsAt.plusSeconds(1800),
        SlotStatus.AVAILABLE,
        null,
        null,
        "STANDARD",
        "Room 1");
  }
}
