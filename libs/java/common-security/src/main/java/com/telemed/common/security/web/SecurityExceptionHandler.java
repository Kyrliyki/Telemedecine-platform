package com.telemed.common.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SecurityExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  public ProblemDetail handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
    pd.setProperty("code", "UNAUTHORIZED");
    pd.setProperty("path", request.getRequestURI());
    return pd;
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    pd.setProperty("code", "ACCESS_DENIED");
    pd.setProperty("path", request.getRequestURI());
    return pd;
  }
}
