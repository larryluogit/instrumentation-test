/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.jakarta.v5_0;

import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;

public class JakartaServletAccessor
    implements ServletAccessor<HttpServletRequest, HttpServletResponse> {
  public static final JakartaServletAccessor INSTANCE = new JakartaServletAccessor();

  private JakartaServletAccessor() {}

  @Override
  public String getRequestContextPath(HttpServletRequest request) {
    return request.getContextPath();
  }

  @Override
  public String getRequestScheme(HttpServletRequest request) {
    return request.getScheme();
  }

  @Override
  public String getRequestServerName(HttpServletRequest request) {
    return request.getServerName();
  }

  @Override
  public int getRequestServerPort(HttpServletRequest request) {
    return request.getServerPort();
  }

  @Override
  public String getRequestUri(HttpServletRequest request) {
    return request.getRequestURI();
  }

  @Override
  public String getRequestQueryString(HttpServletRequest request) {
    return request.getQueryString();
  }

  @Override
  public Object getRequestAttribute(HttpServletRequest request, String name) {
    return request.getAttribute(name);
  }

  @Override
  public void setRequestAttribute(HttpServletRequest request, String name, Object value) {
    request.setAttribute(name, value);
  }

  @Override
  public String getRequestProtocol(HttpServletRequest request) {
    return request.getProtocol();
  }

  @Override
  public String getRequestMethod(HttpServletRequest request) {
    return request.getMethod();
  }

  @Override
  public String getRequestRemoteAddr(HttpServletRequest request) {
    return request.getRemoteAddr();
  }

  @Override
  public String getRequestHeader(HttpServletRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public String getRequestServletPath(HttpServletRequest request) {
    return request.getServletPath();
  }

  @Override
  public Principal getRequestUserPrincipal(HttpServletRequest request) {
    return request.getUserPrincipal();
  }

  @Override
  public Integer getRequestRemotePort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  @Override
  public int getResponseStatus(HttpServletResponse response) {
    return response.getStatus();
  }

  @Override
  public boolean isResponseCommitted(HttpServletResponse response) {
    return response.isCommitted();
  }

  @Override
  public boolean isServletException(Throwable throwable) {
    return throwable instanceof ServletException;
  }
}
