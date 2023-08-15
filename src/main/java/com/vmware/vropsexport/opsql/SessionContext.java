/*
 *
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier:	Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Client;
import java.io.OutputStream;
import java.time.ZoneId;

public class SessionContext {
  private String format;
  private boolean verbose;
  private int numThreads;
  private Client client;
  private int maxRows;
  private int maxRes;
  private final OutputStream output;
  private final long fromTime;
  private final long toTime;

  public SessionContext() {
    output = System.out;
    toTime = System.currentTimeMillis();
    fromTime = toTime - 3600000;
    format = "csv";
  }

  public SessionContext(
      final String format,
      final ZoneId timezone,
      final boolean verbose,
      final int numThreads,
      final Client client,
      final int maxRows,
      final int maxRes,
      final long fromTime,
      final long toTime,
      final OutputStream output) {
    this.format = format;
    this.verbose = verbose;
    this.numThreads = numThreads;
    this.client = client;
    this.maxRows = maxRows;
    this.maxRes = maxRes;
    this.output = output;
    this.fromTime = fromTime;
    this.toTime = toTime;
  }

  public String getFormat() {
    return format;
  }

  public void setFormat(final String format) {
    this.format = format;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public void setVerbose(final boolean verbose) {
    this.verbose = verbose;
  }

  public int getNumThreads() {
    return numThreads;
  }

  public void setNumThreads(final int numThreads) {
    this.numThreads = numThreads;
  }

  public Client getClient() {
    return client;
  }

  public void setClient(final Client client) {
    this.client = client;
  }

  public int getMaxRows() {
    return maxRows;
  }

  public void setMaxRows(final int maxRows) {
    this.maxRows = maxRows;
  }

  public int getMaxRes() {
    return maxRes;
  }

  public void setMaxRes(final int maxRes) {
    this.maxRes = maxRes;
  }

  public OutputStream getOutput() {
    return output;
  }

  public long getFromTime() {
    return fromTime;
  }

  public long getToTime() {
    return toTime;
  }
}
