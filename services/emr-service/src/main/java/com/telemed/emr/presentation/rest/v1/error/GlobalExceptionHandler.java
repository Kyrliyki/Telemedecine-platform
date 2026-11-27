package com.telemed.emr.presentation.rest.v1.error;

import com.telemed.emr.application.exception.MedicalRecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MedicalRecordNotFoundException.class)
  public ProblemDetail handleNotFound(
      MedicalRecordNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "MEDICAL_RECORD_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimistic(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "STALE_MEDICAL_RECORD",
        "Medical record was modified by another request; reload and retry",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleConflict(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "EMR_CONFLICT",
        "The operation conflicts with existing medical data",
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail detail =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", request);
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      errors.put(error.getField(), error.getDefaultMessage());
    }
    detail.setProperty("errors", errors);
    return detail;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", request);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ProblemDetail handleBadRequest(RuntimeException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
  }

  private static ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    problem.setProperty("path", request.getRequestURI());
    return problem;
  }
}
