/*
 * Copyright 2017 VMware, Inc. All Rights Reserved
 *
 * SPDX-License-Identifier: Apache-2.0.
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
package com.vmware.vropsexport;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.exceptions.ValidationException;
import com.vmware.vropsexport.models.NamedResource;
import com.vmware.vropsexport.processors.CSVPrinter;
import com.vmware.vropsexport.processors.JsonPrinter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StatsProcessorTest {

  private static final String VM_ID = "60508ea6-287b-42be-953c-1554e506cdc1";
  private static final String HOST_ID = "e21f1a97-40a4-4e14-ada8-d7ec659f0cf1";
  private static final long START = 1617321169385L;
  private static final long END = 1617323869402L;

  private static class Properties {
    private String resourceId;

    private List<Map<String, String>> property;

    public String getResourceId() {
      return resourceId;
    }

    public void setResourceId(final String resourceId) {
      this.resourceId = resourceId;
    }

    public List<Map<String, String>> getProperty() {
      return property;
    }

    public void setProperty(final List<Map<String, String>> property) {
      this.property = property;
    }
  }

  private NamedResource hostResource;
  private Map<String, String> hostProperties;
  private Map<String, String> vmProperties;
  private List<String> statKeys;

  @Before
  public void init() throws IOException {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    hostResource =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/hostresource.json"), NamedResource.class);

    hostProperties = loadProps("src/test/resources/hostprops.json");
    vmProperties = loadProps("src/test/resources/vmprops.json");
    statKeys =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/statkeys.json"), List.class);
  }

  private static Map<String, String> loadProps(final String file) throws IOException {
    try (final InputStream in = new FileInputStream(file)) {
      final Properties props = new ObjectMapper().readValue(in, Properties.class);
      final Map<String, String> result = new HashMap<>();
      for (final Map<String, String> prop : props.property) {
        result.put(prop.get("name"), prop.get("value"));
      }
      return result;
    }
  }

  @Test
  public void testCSV() throws HttpException, IOException, ExporterException, ValidationException {
    final byte[] data = runTest("vmfields.yaml", new CSVPrinter.Factory());
    final byte[] wanted =
        FileUtils.readFileToByteArray(new File("src/test/resources/csv-output.csv"));
    Assert.assertArrayEquals(wanted, data);
  }

  @Test
  public void testCompactJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact");
  }

  @Test
  public void testCompactJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact-all");
  }

  @Test
  public void testChattyJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty");
  }

  @Test
  public void testChattyJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty-all");
  }

  @Test
  public void testElasticJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic");
  }

  @Test
  public void testElasticJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic-all");
  }

  private void runJSONTest(final String name)
      throws HttpException, IOException, ExporterException, ValidationException {
    final byte[] data = runTest(name + ".yaml", new JsonPrinter.Factory());
    final Map<String, Object> wanted =
        new ObjectMapper()
            .readValue(new File("src/test/resources/" + name + "-output.json"), Map.class);
    final Map<String, Object> actual = new ObjectMapper().readValue(data, Map.class);
    Assert.assertEquals(wanted, actual);
  }

  private byte[] runTest(final String definition, final RowsetProcessorFacotry factory)
      throws IOException, ExporterException, HttpException, ValidationException {
    final DataProvider dp = mock(DataProvider.class);
    when(dp.getResourceName(any())).thenReturn("vm-01");
    when(dp.getRelated(eq(VM_ID), eq("HostSystem"))).thenReturn(hostResource);
    when(dp.fetchMetricStream(any(), any(RowMetadata.class), anyLong(), anyLong()))
        .then((inv) -> new FileInputStream("src/test/resources/hoststats.json"));
    when(dp.fetchProps(eq(HOST_ID))).thenReturn(hostProperties);
    when(dp.fetchProps(eq(VM_ID))).thenReturn(vmProperties);
    when(dp.getStatKeysForResource(eq(VM_ID))).thenReturn(statKeys);

    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/" + definition));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final RowMetadata meta =
        conf.isAllMetrics()
            ? new RowMetadata(conf, dp.getStatKeysForResource(VM_ID))
            : new RowMetadata(conf);
    final StatsProcessor sp = new StatsProcessor(conf, meta, dp, new LRUCache<>(1000), null, false);
    final RowsetProcessor rp = factory.makeFromConfig(out, conf, dp);
    rp.preamble(meta, conf);
    sp.process(new FileInputStream("src/test/resources/vmstats.json"), rp, START, END);
    rp.close();
    return out.toByteArray();
  }
}
