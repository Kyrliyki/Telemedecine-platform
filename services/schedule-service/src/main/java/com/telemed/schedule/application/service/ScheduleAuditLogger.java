package com.telemed.schedule.application.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ScheduleAuditLogger {

  private static final Logger log = LoggerFactory.getLogger("schedule.audit");

  public void workingHoursReplaced(UUID doctorId, int entries) {
    log.info("action=working_hours.replaced doctorId={} entries={}", doctorId, entries);
  }

  public void exceptionCreated(UUID doctorId, UUID exceptionId) {
    log.info("action=exception.created doctorId={} exceptionId={}", doctorId, exceptionId);
  }

  public void exceptionDeleted(UUID doctorId, UUID exceptionId) {
    log.info("action=exception.deleted doctorId={} exceptionId={}", doctorId, exceptionId);
  }

  public void slotHeld(UUID doctorId, UUID slotId, UUID appointmentId) {
    log.info(
        "action=slot.held doctorId={} slotId={} appointmentId={}", doctorId, slotId, appointmentId);
  }

  public void slotConfirmed(UUID doctorId, UUID slotId, UUID appointmentId) {
    log.info(
        "action=slot.confirmed doctorId={} slotId={} appointmentId={}",
        doctorId,
        slotId,
        appointmentId);
  }

  public void slotReleased(UUID doctorId, UUID slotId, UUID appointmentId) {
    log.info(
        "action=slot.released doctorId={} slotId={} appointmentId={}",
        doctorId,
        slotId,
        appointmentId);
  }

  public void slotHoldExpired(UUID doctorId, UUID slotId, UUID appointmentId) {
    log.info(
        "action=slot.hold_expired doctorId={} slotId={} appointmentId={}",
        doctorId,
        slotId,
        appointmentId);
  }
}
