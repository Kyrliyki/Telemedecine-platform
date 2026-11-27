package com.telemed.emr.application.dto.command;

import java.util.List;

public record ReplacePrescriptionsCommand(List<Entry> entries) {

  public record Entry(
      String medicationName,
      String dosage,
      String frequency,
      int durationDays,
      String instructions) {}
}
