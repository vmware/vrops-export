/*
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
 */
package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.Exporter;
import com.vmware.vropsexport.Field;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.models.ResourceRequest;
import com.vmware.vropsexport.utils.ParseUtils;
import java.io.IOException;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpException;

public class Query implements RunnableStatement {
  final ResourceRequest resourceRequest;
  final List<Field> fields;
  private String fromTimeStr;
  private String toTimeStr;
  private Date fromDate;
  private Date toDate;
  private String lookback;
  boolean resolved = false;

  public Query() {
    resourceRequest = new ResourceRequest();
    fields = new LinkedList<>();
  }

  public Query(
      final ResourceRequest resourceRequest,
      final List<Field> fields,
      final String fromTime,
      final String toTime) {
    this.resourceRequest = resourceRequest;
    this.fields = fields;
    fromTimeStr = fromTime;
    toTimeStr = toTime;
  }

  public ResourceRequest getResourceRequest() {
    return resourceRequest;
  }

  public List<Field> getFields() {
    return fields;
  }

  public void setFromTimeStr(final String fromTimeStr) {
    if (lookback != null) {
      throw new OpsqlException("Relative and absolute time ranges are mutually exclusive");
    }
    this.fromTimeStr = fromTimeStr;
  }

  public void setToTimeStr(final String toTimeStr) {
    if (lookback != null) {
      throw new OpsqlException("Relative and absolute time ranges are mutually exclusive");
    }
    this.toTimeStr = toTimeStr;
  }

  public String getLookback() {
    return lookback;
  }

  public void setLookback(final String lookback) {
    if (fromTimeStr != null || toTimeStr != null) {
      throw new OpsqlException("Relative and absolute time ranges are mutually exclusive");
    }
    this.lookback = lookback;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query query = (Query) o;
    return Objects.equals(resourceRequest, query.resourceRequest)
        && Objects.equals(fields, query.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceRequest, fields);
  }

  public Config toConfig(final SessionContext ctx) {
    final Config conf = ctx.getConfig();
    conf.setQuery(resourceRequest);
    conf.setFields(fields);
    return conf;
  }

  @Override
  public void run(final SessionContext ctx) throws ExporterException {
    final Config conf = toConfig(ctx);
    final Exporter exporter =
        new Exporter(
            ctx.getClient(),
            ctx.getNumThreads(),
            conf,
            ctx.isVerbose(),
            true,
            ctx.getMaxRows(),
            ctx.getMaxRes());
    final long begin = fromTimeStr != null ? parseDate(fromTimeStr, ctx) : ctx.getFromTime();
    final long end = toTimeStr != null ? parseDate(toTimeStr, ctx) : ctx.getToTime();
    try {
      exporter.exportTo(ctx.getOutput(), begin, end, null, true);
    } catch (final IOException | HttpException e) {
      throw new ExporterException(e);
    }
  }

  public void resolveDates(final SessionContext ctx) throws ExporterException {
    if (resolved) {
      return;
    }
    if (fromTimeStr != null || lookback != null) {
      fromDate =
          new Date(
              lookback != null
                  ? System.currentTimeMillis() - ParseUtils.parseLookback(lookback)
                  : parseDate(fromTimeStr, ctx));
    }
    if (toTimeStr != null || lookback != null) {
      toDate = new Date(lookback != null ? System.currentTimeMillis() : parseDate(toTimeStr, ctx));
    }
    resolved = true;
  }

  private long parseDate(final String date, final SessionContext ctx) {
    // Attempt to parse as datetime. If that doesn't work, try with time only.
    try {
      return ParseUtils.parseDateTime(date, ctx.getConfig().getZoneId()).getTime();
    } catch (final DateTimeParseException e) {
      return ParseUtils.parseTime(date, ctx.getConfig().getZoneId()).getTime();
    }
  }

  public Date getFromDate() {
    return fromDate;
  }

  public Date getToDate() {
    return toDate;
  }
}
