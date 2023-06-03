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
package com.vmware.vropsexport.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.vmware.vropsexport.*;
import com.vmware.vropsexport.exceptions.ExporterException;
import org.apache.http.HttpException;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

public class JsonProducer {
  private final JsonGenerator generator;

  private final DataProvider dp;

  private final DateFormat dateFormat;

  public JsonProducer(
      final JsonGenerator generator, final DataProvider dp, final DateFormat dateFormat) {
    this.generator = generator;
    this.dp = dp;
    this.dateFormat = dateFormat;
  }

  public void produce(
      final Rowset rowset, final RowMetadata meta, final JsonConfig.JsonFormat format)
      throws ExporterException {
    synchronized (generator) {
      try {
        switch (format) {
          case compact:
            produceCompact(rowset, meta);
            break;
          case chatty:
            produceChatty(rowset, meta);
            break;
          case elastic:
            produceElastic(rowset, meta);
            break;
        }
      } catch (final IOException | HttpException e) {
        throw new ExporterException(e);
      }
    }
  }

  public void produceCompact(final Rowset rowset, final RowMetadata meta)
      throws IOException, HttpException {
    generator.writeStartObject(); // {
    generator.writeStringField("resourceName", dp.getResourceName(rowset.getResourceId()));

    // Properties
    if (!rowset.getRows().isEmpty()) {
      final Row firstRow = rowset.getRows().firstEntry().getValue();
      generator.writeArrayFieldStart("properties");
      for (final String propertyName : meta.getPropMap().keySet()) {
        final int propIndex = meta.getPropertyIndex(propertyName);
        final String v = firstRow.getProp(propIndex);
        if (v == null) {
          continue;
        }
        generator.writeStartObject();
        generator.writeStringField("k", meta.getAliasForProp(propertyName));
        generator.writeStringField("v", v);
        generator.writeEndObject();
      }
      generator.writeEndArray();
    }

    // Metrics
    generator.writeArrayFieldStart("metrics");
    int i = 0;
    for (final Field f : meta.getFields()) {
      generator.writeStartObject();
      generator.writeStringField("name", f.getAlias());
      generator.writeArrayFieldStart("samples");
      for (final Map.Entry<Long, Row> row : rowset.getRows().entrySet()) {
        final int metricIndex = meta.getMetricIndex(i);
        final Double v = row.getValue().getMetric(i);
        if (v == null) {
          continue;
        }
        generator.writeStartObject();
        generator.writeStringField("t", toDate(row.getKey()));
        generator.writeNumberField("v", v);
        generator.writeEndObject();
      }
      generator.writeEndArray();
      generator.writeEndObject();
    }
    generator.writeEndArray();
    generator.writeEndObject();
    ++i;
  }

  public void produceChatty(final Rowset rowset, final RowMetadata meta)
      throws IOException, HttpException {
    for (final Map.Entry<Long, Row> row : rowset.getRows().entrySet()) {
      int i = 0;
      for (final Field f : meta.getFields()) {
        final Double v = row.getValue().getMetric(i);
        if (v == null) {
          continue;
        }
        generator.writeStartObject();
        generator.writeStringField("t", toDate(row.getKey()));
        generator.writeStringField("resourceName", dp.getResourceName(rowset.getResourceId()));
        generator.writeStringField("metric", f.getAlias());
        generator.writeNumberField("v", v);
        generator.writeEndObject();
        i++;
      }
    }
  }

  public void produceElastic(final Rowset rowset, final RowMetadata meta)
      throws IOException, HttpException {
    for (final Map.Entry<Long, Row> row : rowset.getRows().entrySet()) {
      produceSingleElastic(row.getValue(), row.getKey(), rowset.getResourceId(), meta);
    }
  }

  public void produceSingleElastic(
      final Row row, final long timestamp, final String resourceId, final RowMetadata meta)
      throws IOException, HttpException {
    generator.writeStartObject();
    generator.writeStringField("resourceName", dp.getResourceName(resourceId));
    generator.writeStringField("t", toDate(timestamp));
    int i = 0;
    for (final Field f : meta.getFields()) {
      final Double v = row.getMetric(i);
      if (v == null) {
        continue;
      }
      generator.writeNumberField(f.getAlias(), v);
      i++;
    }
    generator.writeEndObject();
  }

  private String toDate(final long l) {
    return dateFormat.format(new Date(l));
  }
}
