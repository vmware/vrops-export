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
package com.vmware.vropsexport.processors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.json.JsonConfig;
import com.vmware.vropsexport.json.JsonProducer;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;

public class JsonPrinter implements RowsetProcessor {
  public static class Factory implements RowsetProcessorFacotry {

    @Override
    public RowsetProcessor makeFromConfig(
        final OutputStream out, final Config config, final DataProvider dp)
        throws ExporterException {
      return new JsonPrinter(
          out,
          dp,
          config.getJsonConfig() != null
              ? config.getJsonConfig().getFormat()
              : JsonConfig.JsonFormat.compact,
          config.getDateFormatter(),
          config.getJsonConfig().isPretty());
    }

    @Override
    public boolean isProducingOutput() {
      return true;
    }
  }

  private final JsonGenerator generator;

  private final JsonConfig.JsonFormat format;

  private final JsonProducer producer;

  public JsonPrinter(
      final OutputStream out,
      final DataProvider dp,
      final JsonConfig.JsonFormat format,
      final DateFormat dateFormat,
      final boolean pretty)
      throws ExporterException {
    try {
      this.format = format;
      final JsonFactory jf = new JsonFactory();
      generator = jf.createGenerator(out, JsonEncoding.UTF8);
      generator.overrideStdFeatures(0, JsonGenerator.Feature.AUTO_CLOSE_TARGET.getMask());
      if (pretty) {
        generator.useDefaultPrettyPrinter();
      }
      producer = new JsonProducer(generator, dp, dateFormat);
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {
    try {
      generator.writeStartObject();
      generator.writeArrayFieldStart("data");
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
    producer.produce(rowset, meta, format);
  }

  @Override
  public void close() throws ExporterException {
    try {
      generator.writeEndArray();
      generator.writeEndObject();
      generator.writeRaw('\n');
      generator.close();
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }
}
