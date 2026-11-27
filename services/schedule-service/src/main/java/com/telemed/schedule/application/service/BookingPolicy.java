package com.telemed.schedule.application.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BookingPolicy {

  private final Duration leadTime;
  private final Duration maxAdvance;

  public BookingPolicy(
      @Value("${schedule.booking.lead-time:1h}") Duration leadTime,
      @Value("${schedule.booking.max-advance:60d}") Duration maxAdvance) {
    this.leadTime = leadTime;
    this.maxAdvance = maxAdvance;
  }

  public boolean isBookable(Instant startsAt, Instant now) {
    return !startsAt.isBefore(now.plus(leadTime)) && !startsAt.isAfter(now.plus(maxAdvance));
  }

  public Duration leadTime() {
    return leadTime;
  }

  public Duration maxAdvance() {
    return maxAdvance;
  }
}
