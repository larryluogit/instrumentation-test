/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jsf;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import javax.faces.FacesException;
import javax.faces.component.ActionSource2;
import javax.faces.component.UIViewRoot;
import javax.faces.context.FacesContext;
import javax.faces.el.EvaluationException;
import javax.faces.el.MethodNotFoundException;
import javax.faces.event.ActionEvent;

public abstract class JsfTracer extends BaseTracer {

  public Span startSpan(ActionEvent event) {
    // https://jakarta.ee/specifications/faces/2.3/apidocs/index.html?javax/faces/component/ActionSource2.html
    // ActionSource2 was added in JSF 1.2 and is implemented by components that have an action
    // attribute such as a button or a link
    if (event.getComponent() instanceof ActionSource2) {
      ActionSource2 actionSource = (ActionSource2) event.getComponent();
      if (actionSource.getActionExpression() != null) {
        // either an el expression in the form #{bean.method()} or navigation case name
        String expressionString = actionSource.getActionExpression().getExpressionString();
        // start span only if it an expression
        if (expressionString.startsWith("#{") || expressionString.startsWith("${")) {
          return tracer.spanBuilder(expressionString).startSpan();
        }
      }
    }

    return null;
  }

  public void updateServerSpanName(Context context, FacesContext facesContext) {
    Span serverSpan = getCurrentServerSpan();
    if (serverSpan == null) {
      return;
    }

    UIViewRoot uiViewRoot = facesContext.getViewRoot();
    if (uiViewRoot == null) {
      return;
    }

    String viewId = uiViewRoot.getViewId();
    serverSpan.updateName(ServletContextPath.prepend(context, viewId));
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof FacesException) {
      Throwable cause = throwable.getCause();
      if (cause instanceof EvaluationException && cause.getCause() != null) {
        throwable = cause.getCause();
      } else if (cause instanceof MethodNotFoundException) {
        throwable = cause;
      }
    }
    return super.unwrapThrowable(throwable);
  }
}
