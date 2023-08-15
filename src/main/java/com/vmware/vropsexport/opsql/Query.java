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
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.apache.http.HttpException;

public class Query implements RunnableStatement {
  final ResourceRequest resourceRequest;
  final List<Field> fields;
  Date fromTime;
  Date toTime;

  public Query() {
    resourceRequest = new ResourceRequest();
    fields = new LinkedList<>();
  }

  public Query(
      final ResourceRequest resourceRequest,
      final List<Field> fields,
      final Date fromTime,
      final Date toTime) {
    this.resourceRequest = resourceRequest;
    this.fields = fields;
    this.fromTime = fromTime;
    this.toTime = toTime;
  }

  public ResourceRequest getResourceRequest() {
    return resourceRequest;
  }

  public List<Field> getFields() {
    return fields;
  }

  public Date getFromTime() {
    return fromTime;
  }

  public Date getToTime() {
    return toTime;
  }

  public void setFromTime(final Date fromTime) {
    this.fromTime = fromTime;
  }

  public void setToTime(final Date toTime) {
    this.toTime = toTime;
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
    final Config conf = new Config();
    conf.setQuery(resourceRequest);
    conf.setFields(fields);
    conf.setOutputFormat(ctx.getFormat());
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
    final long begin = fromTime != null ? fromTime.getTime() : ctx.getFromTime();
    final long end = toTime != null ? toTime.getTime() : ctx.getToTime();
    try {
      exporter.exportTo(ctx.getOutput(), begin, end, null, true);
    } catch (final IOException | HttpException e) {
      throw new ExporterException(e);
    }
  }
}
