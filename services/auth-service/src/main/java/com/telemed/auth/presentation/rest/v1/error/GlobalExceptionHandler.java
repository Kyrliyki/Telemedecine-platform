package com.telemed.auth.presentation.rest.v1.error;

import com.telemed.auth.application.exception.EmailAlreadyRegisteredException;
import com.telemed.auth.application.exception.InvalidCredentialsException;
import com.telemed.auth.application.exception.UserNotFoundException;
import com.telemed.auth.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
    HttpStatus status =
        switch (ex.code()) {
          case "INVALID_CREDENTIALS" -> HttpStatus.UNAUTHORIZED;
          case "USER_BLOCKED", "ROLE_ASSIGNMENT_NOT_ALLOWED" -> HttpStatus.FORBIDDEN;
          case "EMAIL_ALREADY_EXISTS" -> HttpStatus.CONFLICT;
          case "OAUTH_ACCOUNT_CONFLICT", "OAUTH_ALREADY_LINKED", "INVALID_STATUS_TRANSITION" ->
              HttpStatus.CONFLICT;
          case "INVITATION_EXPIRED" -> HttpStatus.GONE;
          default -> HttpStatus.BAD_REQUEST;
        };
    return problem(status, ex.code(), ex.getMessage(), request);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  public ProblemDetail handleInvalidCredentials(
      InvalidCredentialsException ex, HttpServletRequest request) {
    return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", ex.getMessage(), request);
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  public ProblemDetail handleEmailRegistered(
      EmailAlreadyRegisteredException ex, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex.getMessage(), request);
  }

  @ExceptionHandler(UserNotFoundException.class)
  public ProblemDetail handleUserNotFound(UserNotFoundException ex, HttpServletRequest request) {
    return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleConcurrentModification(
      OptimisticLockingFailureException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "The resource was modified concurrently, retry the request",
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
  public ProblemDetail handleUnreadableBody(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request body is not valid JSON", request);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ProblemDetail handleBadRequest(RuntimeException ex, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(), request);
  }

  private ProblemDetail problem(
      HttpStatus status, String code, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setProperty("code", code);
    problem.setProperty("path", request.getRequestURI());
    return problem;
  }
}
