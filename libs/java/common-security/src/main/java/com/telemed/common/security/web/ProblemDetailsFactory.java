package com.telemed.common.security.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailsFactory {

  private ProblemDetailsFactory() {}

  public static ProblemDetail of(HttpStatus status, String code, String detail, String path) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setProperty("code", code);
    if (path != null) pd.setProperty("path", path);
    return pd;
  }
}
