package com.telemed.schedule.application.service;

import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.infrastructure.client.AppointmentStatusClient;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AppointmentSlotReconciliationJob {

  private static final Logger log = LoggerFactory.getLogger(AppointmentSlotReconciliationJob.class);

  private final TimeSlotRepository timeSlotRepository;
  private final SlotReservationService reservationService;
  private final AppointmentStatusClient statusClient;
  private final Clock clock;
  private final boolean enabled;

  public AppointmentSlotReconciliationJob(
      TimeSlotRepository timeSlotRepository,
      SlotReservationService reservationService,
      AppointmentStatusClient statusClient,
      Clock clock,
      @Value("${schedule.appointment-service.reconcile-enabled:false}") boolean enabled) {
    this.timeSlotRepository = timeSlotRepository;
    this.reservationService = reservationService;
    this.statusClient = statusClient;
    this.clock = clock;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelayString = "${schedule.appointment-service.reconcile-interval:3600000}")
  public void reconcile() {
    if (!enabled) {
      return;
    }
    for (TimeSlot slot : timeSlotRepository.findConfirmed(clock.now(), 200)) {
      if (slot.getAppointmentId() == null) {
        continue;
      }
      try {
        Optional<String> status = statusClient.status(slot.getAppointmentId().toString());
        if (status.isEmpty() || isTerminal(status.get())) {
          reservationService.release(new ReleaseSlotCommand(slot.getAppointmentId()));
          log.info(
              "Released slot {} for terminal/missing appointment {}",
              slot.getId(),
              slot.getAppointmentId());
        }
      } catch (Exception exception) {
        log.warn(
            "Appointment reconciliation failed for slot {} appointment {}",
            slot.getId(),
            slot.getAppointmentId(),
            exception);
      }
    }
  }

  private static boolean isTerminal(String status) {
    String normalized = status.toUpperCase(Locale.ROOT);
    return normalized.equals("CANCELLED")
        || normalized.equals("DELETED")
        || normalized.equals("COMPLETED");
  }
}
