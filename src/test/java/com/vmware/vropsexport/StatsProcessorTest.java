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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.exceptions.ValidationException;
import com.vmware.vropsexport.models.NamedResource;
import com.vmware.vropsexport.processors.CSVPrinter;
import com.vmware.vropsexport.processors.JsonPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.*;

import static org.mockito.AdditionalMatchers.or;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatsProcessorTest {

  private static final String VM_ID_1 = "60508ea6-287b-42be-953c-1554e506cdc1";
  private static final String VM_ID_2 = "60508ea6-287b-42be-953c-1554e506cdc2";
  private static final String VM_NAME = "vm-01";
  private static final String HOST_ID = "e21f1a97-40a4-4e14-ada8-d7ec659f0cf1";
  private static final String HOST_NAME = "MyHost";
  private static final long START = 1617321169385L;
  private static final long END = 1617323869402L;

  private static class Properties {
    private String resourceId;

    private List<Map<String, String>> property;

    @SuppressWarnings("unused")
    public String getResourceId() {
      return resourceId;
    }

    @SuppressWarnings("unused")
    public void setResourceId(final String resourceId) {
      this.resourceId = resourceId;
    }

    @SuppressWarnings("unused")
    public List<Map<String, String>> getProperty() {
      return property;
    }

    @SuppressWarnings("unused")
    public void setProperty(final List<Map<String, String>> property) {
      this.property = property;
    }
  }

  private NamedResource hostResource;
  private List<NamedResource> vmResources;
  private Map<String, String> hostProperties;
  private Map<String, String> vmProperties;
  private List<String> statKeys;

  @Before
  @SuppressWarnings("unchecked")
  public void init() throws IOException {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    hostResource =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/hostresource.json"), NamedResource.class);
    vmResources =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/vmresources.json"), List.class);

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
    final byte[] data = runTest("vmfields.yaml", "vmstats", new CSVPrinter.Factory());
    final byte[] wanted =
        FileUtils.readFileToByteArray(new File("src/test/resources/csv-output.csv"));
    Assert.assertArrayEquals(wanted, data);
  }

  @Test
  public void testCompactJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact", "vmstats");
  }

  @Test
  public void testCompactJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact-all", "vmstats");
  }

  @Test
  public void testChattyJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty", "vmstats");
  }

  @Test
  public void testChattyJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty-all", "vmstats");
  }

  @Test
  public void testElasticJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic", "vmstats");
  }

  @Test
  public void testElasticJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic-all", "vmstats");
  }

  @Test
  public void testChildren()
      throws ValidationException, ExporterException, HttpException, IOException {
    runJSONTest("children", "hoststats");
  }

  @SuppressWarnings("unchecked")
  private void runJSONTest(final String name, final String metricInput)
      throws HttpException, IOException, ExporterException, ValidationException {
    final byte[] data = runTest(name + ".yaml", metricInput, new JsonPrinter.Factory());
    final ObjectMapper om = new ObjectMapper();
    final Map<String, Object> wanted =
        om.readValue(new File("src/test/resources/" + name + "-output.json"), Map.class);
    final Map<String, Object> actual = om.readValue(data, Map.class);
    Assert.assertEquals(wanted, actual);
  }

  private DataProvider getMockedDataProvider() throws HttpException, IOException {
    final DataProvider dp = mock(DataProvider.class);
    when(dp.getResourceName(eq(VM_ID_1))).thenReturn("vm-01");
    when(dp.getResourceName(eq(HOST_ID))).thenReturn("vm-01");
    when(dp.getRelativesOf(
            eq(Field.RelationshipType.parent),
            or(eq(VM_ID_1), eq(VM_ID_2)),
            eq("VMWARE"),
            eq("HostSystem"),
            eq(1)))
        .thenReturn(Collections.singletonList(hostResource));
    when(dp.getRelativesOf(
            eq(Field.RelationshipType.child),
            eq(HOST_ID),
            eq("VMWARE"),
            eq("VirtualMachine"),
            eq(1)))
        .thenReturn(vmResources);
    when(dp.fetchMetricStream(any(), any(RowMetadata.class), anyLong(), anyLong()))
        .then((inv) -> new FileInputStream("src/test/resources/hoststats.json"));
    when(dp.fetchProps(eq(HOST_ID))).thenReturn(hostProperties);
    when(dp.fetchProps(eq(VM_ID_1))).thenReturn(vmProperties);
    when(dp.getStatKeysForResource(eq(VM_ID_1))).thenReturn(statKeys);
    return dp;
  }

  private byte[] runTest(
      final String definition, final String metricInput, final RowsetProcessorFacotry factory)
      throws IOException, ExporterException, HttpException, ValidationException {
    final DataProvider dp = getMockedDataProvider();

    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/" + definition));
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final RowMetadata meta =
        conf.isAllMetrics()
            ? new RowMetadata(conf, dp.getStatKeysForResource(VM_ID_1))
            : new RowMetadata(conf);
    final StatsProcessor sp = new StatsProcessor(conf, meta, dp, new LRUCache<>(1000), null, false);
    final RowsetProcessor rp = factory.makeFromConfig(out, conf, dp);
    rp.preamble(meta, conf);
    sp.process(new FileInputStream("src/test/resources/" + metricInput + ".json"), rp, START, END);
    rp.close();
    return out.toByteArray();
  }
}
