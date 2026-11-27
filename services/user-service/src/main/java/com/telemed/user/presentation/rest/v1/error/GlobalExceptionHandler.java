package com.telemed.user.presentation.rest.v1.error;

import com.telemed.user.application.exception.DoctorProfileNotFoundException;
import com.telemed.user.application.exception.EmailAlreadyInUseException;
import com.telemed.user.application.exception.UserNotFoundException;
import com.telemed.user.domain.exception.InvalidVerificationTransitionException;
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

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(DoctorProfileNotFoundException.class)
  public ProblemDetail handleDoctorNotFound(
      DoctorProfileNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "DOCTOR_PROFILE_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(EmailAlreadyInUseException.class)
  public ProblemDetail handleEmailConflict(
      EmailAlreadyInUseException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_IN_USE", ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidVerificationTransitionException.class)
  public ProblemDetail handleInvalidVerification(
      InvalidVerificationTransitionException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT, "INVALID_VERIFICATION_TRANSITION", ex.getMessage(), request);
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
  public ProblemDetail handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleConflict(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "DATA_CONFLICT",
        "The operation conflicts with existing data",
        request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "STALE_PROFILE",
        "The profile was modified by another request; reload and retry",
        request);
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
