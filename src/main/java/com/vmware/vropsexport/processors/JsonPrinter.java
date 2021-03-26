/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport.processors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import org.apache.http.HttpException;

public class JsonPrinter implements RowsetProcessor {
  public static class Factory implements RowsetProcessorFacotry {

    @Override
    public RowsetProcessor makeFromConfig(
        final OutputStream out, final Config config, final DataProvider dp)
        throws ExporterException {
      return new JsonPrinter(out, dp);
    }

    @Override
    public boolean isProducingOutput() {
      return true;
    }
  }

  private final OutputStream out;

  private final JsonGenerator generator;

  private final DataProvider dp;

  public JsonPrinter(final OutputStream out, final DataProvider dp) throws ExporterException {
    try {
      this.dp = dp;
      this.out = out;
      final JsonFactory jf = new JsonFactory();
      generator = jf.createGenerator(out, JsonEncoding.UTF8);
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {
    try {
      generator.writeStartObject();
      generator.writeArrayFieldStart("resources");
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
    synchronized (out) {
      try {
        generator.writeStartObject(); // {
        generator.writeStringField(
            "resourceName", dp.getResourceName(rowset.getResourceId())); // resourceName: 'x'

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
        generator.writeArrayFieldStart("metrics"); // metrics: [
        for (final String metricName : meta.getMetricMap().keySet()) {
          generator.writeStartObject(); // {
          final int metricIndex = meta.getMetricIndex(metricName);
          generator.writeStringField("name", meta.getAliasForMetric(metricName)); // name: 'x'
          generator.writeArrayFieldStart("samples"); // samples: [
          for (final Map.Entry<Long, Row> row : rowset.getRows().entrySet()) {
            final Double v = row.getValue().getMetric(metricIndex);
            if (v == null) {
              continue;
            }
            generator.writeStartObject(); // {
            generator.writeNumberField("t", row.getKey()); // t: 28349387
            generator.writeNumberField("v", v); // v: 234983279874
            generator.writeEndObject(); // }
          }
          generator.writeEndArray(); // ]
          generator.writeEndObject(); // }
        }
        generator.writeEndArray(); // ]
        generator.writeEndObject(); // }
      } catch (final IOException | HttpException e) {
        throw new ExporterException(e);
      }
    }
  }

  @Override
  public void close() throws ExporterException {
    try {
      generator.writeEndArray();
      generator.writeEndObject();
      generator.close();
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }
}
