/*
 * SonarQube Java
 * Copyright (C) 2012-2021 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.stream.Collectors;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class ExecutionTimeReport {
  private static final Logger LOG = Loggers.get(ExecutionTimeReport.class);

  private static final int MAX_REPORTED_FILES = 10;
  private static final long MIN_REPORTED_ANALYSIS_TIME_MS = 1000;
  private static final long MIN_TOTAL_ANALYSIS_TIME_TO_REPORT_MS = 20_000;

  private static class ExecutionTime {
    private final String file;
    private final long analysisTime;

    public ExecutionTime(String file, long analysisTime) {
      this.file = file;
      this.analysisTime = analysisTime;
    }
  }

  private static final Comparator<ExecutionTime> ORDER_BY_ANALYSIS_TIME_DESCENDING_AND_FILE_ASCENDING = (a, b) -> {
    int compare = Long.compare(b.analysisTime, a.analysisTime);
    return compare != 0 ? compare : a.file.compareTo(b.file);
  };

  private final LinkedList<ExecutionTime> recordedOrderedExecutionTime = new LinkedList<>();
  private long minRecordedOrderedExecutionTime = MIN_REPORTED_ANALYSIS_TIME_MS;

  private final Clock clock;
  private final long analysisStartTimeMS;
  private String currentFile;
  private long currentFileStartTimeMS;

  public ExecutionTimeReport(Clock clock) {
    this.clock = clock;
    analysisStartTimeMS = clock.millis();
  }

  public void start(String currentFile) {
    this.currentFile = currentFile;
    currentFileStartTimeMS = clock.millis();
  }

  public void end() {
    long currentAnalysisTime = clock.millis() - currentFileStartTimeMS;
    if (LOG.isTraceEnabled()) {
      LOG.trace("Analysis time of " + currentFile + " (" + currentAnalysisTime + "ms)");
    } else if (currentAnalysisTime >= MIN_REPORTED_ANALYSIS_TIME_MS && LOG.isDebugEnabled()) {
      LOG.debug("Analysis time of " + currentFile + " (" + currentAnalysisTime + "ms)");
    }
    if (currentAnalysisTime >= minRecordedOrderedExecutionTime) {
      recordedOrderedExecutionTime.add(new ExecutionTime(currentFile, currentAnalysisTime));
      recordedOrderedExecutionTime.sort(ORDER_BY_ANALYSIS_TIME_DESCENDING_AND_FILE_ASCENDING);
      if (recordedOrderedExecutionTime.size() > MAX_REPORTED_FILES) {
        recordedOrderedExecutionTime.removeLast();
        minRecordedOrderedExecutionTime = recordedOrderedExecutionTime.stream()
          .mapToLong(e -> e.analysisTime)
          .min()
          .orElse(MIN_REPORTED_ANALYSIS_TIME_MS);
      }
    }
    this.currentFile = null;
  }

  public void report() {
    if (currentFile != null) {
      end();
    }
    long analysisEndTimeMS = clock.millis() - analysisStartTimeMS;
    if (analysisEndTimeMS >= MIN_TOTAL_ANALYSIS_TIME_TO_REPORT_MS && !recordedOrderedExecutionTime.isEmpty()) {
      LOG.info("Slowest analyzed files:" + System.lineSeparator() + toString());
    }
  }

  @Override
  public String toString() {
    return recordedOrderedExecutionTime.stream()
      .map(e -> "    " + e.file + " (" + e.analysisTime + "ms)")
      .collect(Collectors.joining(System.lineSeparator()));
  }

}