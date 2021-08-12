/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry.results;

import io.opentelemetry.config.TestConfig;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

class PrintStreamPersister implements ResultsPersister {

  private final PrintStream out;

  public PrintStreamPersister(PrintStream out) {this.out = out;}

  @Override
  public void write(List<AppPerfResults> results) {
    TestConfig config = results.stream().findFirst().get().config;
    out.println("----------------------------------------------------------");
    out.println(" Run at " + new Date());
    out.printf(" %s : %s\n", config.getName(), config.getDescription());
    out.printf(" %d users, %d iterations\n", config.getConcurrentConnections(), config.getTotalIterations());
    out.println("----------------------------------------------------------");

    display(results, "Agent", appPerfResults -> appPerfResults.agent.getName());
    display(results, "Startup time (ms)", res -> String.valueOf(res.startupDurationMs));
    display(results, "Total allocated MB", res -> format(res.getTotalAllocatedMB()));
    display(results, "Heap (min)", res -> String.valueOf(res.heapUsed.min));
    display(results, "Heap (max)", res -> String.valueOf(res.heapUsed.max));
    display(results, "Thread switch rate",
        res -> String.valueOf(res.maxThreadContextSwitchRate));
    display(results, "GC time", res -> String.valueOf(res.totalGCTime));
    display(results, "Req. mean", res -> format(res.requestAvg));
    display(results, "Req. p95", res -> format(res.requestP95));
    display(results, "Iter. mean", res -> format(res.iterationAvg));
    display(results, "Iter. p95", res -> format(res.iterationP95));
    display(results, "Peak threads", res -> String.valueOf(res.peakThreadCount));
  }

  private void display(List<AppPerfResults> results, String pref,
      Function<AppPerfResults, String> vs) {
    out.printf("%-20s: ", pref);
    results.stream()
        .sorted(Comparator.comparing(AppPerfResults::getAgentName))
        .forEach(result -> {
          out.printf("%17s", vs.apply(result));
      });
    out.println();
  }

  private String format(double d) {
    return String.format("%.2f", d);
  }

}
