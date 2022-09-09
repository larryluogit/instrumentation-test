/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

import javax.management.ObjectName;
import javax.management.QueryExp;

/**
 * A class describing a set of MBeans which can be used to collect values for a metric. Objects of
 * this class are inmutable.
 */
public class BeanPack {
  // How to specify the MBean(s)
  private final QueryExp queryExp;
  private final ObjectName[] namePatterns;

  /**
   * Constructor for BeanPack.
   *
   * @param queryExp the QueryExp to be used to filter results when looking for MBeans
   * @param namePatterns an array of ObjectNames used to look for MBeans; usually they will be
   *     patterns. If multiple patterns are provided, they work as logical OR.
   */
  public BeanPack(QueryExp queryExp, ObjectName... namePatterns) {
    this.queryExp = queryExp;
    this.namePatterns = namePatterns;
  }

  QueryExp getQueryExp() {
    return queryExp;
  }

  ObjectName[] getNamePatterns() {
    return namePatterns;
  }
}
