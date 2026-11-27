package com.telemed.schedule.application.exception;

public class SlotUnavailableException extends RuntimeException {

  public SlotUnavailableException(String message) {
    super(message);
  }
}
