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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.exceptions.ValidationException;
import com.vmware.vropsexport.models.NamedResource;
import com.vmware.vropsexport.opsql.Query;
import com.vmware.vropsexport.opsql.QueryCompiler;
import com.vmware.vropsexport.processors.CSVPrinter;
import com.vmware.vropsexport.processors.JsonPrinter;
import com.vmware.vropsexport.utils.LRUCache;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
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

  private DataProvider dataProvider;

  @Before
  @SuppressWarnings("unchecked")
  public void init() throws IOException, HttpException {
    TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
    final NamedResource hostResource =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/hostresource.json"), NamedResource.class);
    final List<NamedResource> vmResources =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(
                new File("src/test/resources/vmresources.json"),
                new TypeReference<List<NamedResource>>() {});

    final Map<String, String> hostProperties = loadProps("src/test/resources/hostprops.json");
    final Map<String, String> vmProperties1 = loadProps("src/test/resources/vmprops1.json");
    final Map<String, String> vmProperties2 = loadProps("src/test/resources/vmprops2.json");
    final List<String> statKeys =
        new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(new File("src/test/resources/statkeys.json"), List.class);
    dataProvider = mock(DataProvider.class);
    when(dataProvider.getResourceName(eq(VM_ID_1))).thenReturn("vm-01");
    when(dataProvider.getResourceName(eq(VM_ID_2))).thenReturn("vm-02");
    when(dataProvider.getResourceName(eq(HOST_ID))).thenReturn("MyHost");
    when(dataProvider.getRelativesOf(
            eq(Field.RelationshipType.parent),
            or(eq(VM_ID_1), eq(VM_ID_2)),
            eq("VMWARE"),
            eq("HostSystem"),
            eq(1)))
        .thenReturn(Collections.singletonList(hostResource));
    when(dataProvider.getRelativesOf(
            eq(Field.RelationshipType.child),
            eq(HOST_ID),
            eq("VMWARE"),
            eq("VirtualMachine"),
            eq(1)))
        .thenReturn(vmResources);
    when(dataProvider.fetchMetricStream(any(), any(RowMetadata.class), anyLong(), anyLong()))
        .then((inv) -> new FileInputStream("src/test/resources/hoststats.json"));
    when(dataProvider.fetchProps(eq(HOST_ID))).thenReturn(hostProperties);
    when(dataProvider.fetchProps(eq(VM_ID_1))).thenReturn(vmProperties1);
    when(dataProvider.fetchProps(eq(VM_ID_2))).thenReturn(vmProperties2);
    when(dataProvider.getStatKeysForResource(eq(VM_ID_1))).thenReturn(statKeys);
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
    final byte[] data = runTest("vmfields.yaml", "singlevmstats", new CSVPrinter.Factory());
    final byte[] wanted =
        FileUtils.readFileToByteArray(new File("src/test/resources/csv-output.csv"));
    Assert.assertEquals(
        new String(wanted, StandardCharsets.UTF_8), new String(data, StandardCharsets.UTF_8));
  }

  @Test
  public void testCompactJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact", "singlevmstats");
  }

  @Test
  public void testCompactJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("compact-all", "singlevmstats");
  }

  @Test
  public void testChattyJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty", "singlevmstats");
  }

  @Test
  public void testChattyJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("chatty-all", "singlevmstats");
  }

  @Test
  public void testElasticJSON()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic", "singlevmstats");
  }

  @Test
  public void testElasticJSONAll()
      throws HttpException, IOException, ExporterException, ValidationException {
    runJSONTest("elastic-all", "singlevmstats");
  }

  @Test
  public void testChildren()
      throws ValidationException, ExporterException, HttpException, IOException {
    runJSONTest("children", "hoststats");
  }

  @Test
  public void testChildQuery()
      throws ValidationException, ExporterException, HttpException, IOException {
    runQueryTest(
        "resource(VMWARE:HostSystem).children(VMWARE:VirtualMachine vm).fields(cpu|demandPct cpuDemand, avg(vm->cpu|demandmhz) vmCpuDemand)",
        "children",
        "hoststats");
  }

  @Test
  public void testParentQuery()
      throws ValidationException, ExporterException, HttpException, IOException {
    runQueryTest(
        "resource(VMWARE:VirtualMachine).parents(VMWARE:HostSystem h).fields(cpu|demandPct cpuDemand, @summary|fullGuestName, avg(h->cpu|demandmhz) hostCpuDemand, h->@cpu|cpuModel)",
        "parent",
        "vmstats");
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

  private void runQueryTest(final String query, final String name, final String metricInput)
      throws HttpException, IOException, ExporterException, ValidationException {
    final Query q = QueryCompiler.compile(query);
    final byte[] data = runTest(q.toConfig(), metricInput, new JsonPrinter.Factory());
    final ObjectMapper om = new ObjectMapper();
    final Map<String, Object> wanted =
        om.readValue(new File("src/test/resources/" + name + "-output.json"), Map.class);
    final Map<String, Object> actual = om.readValue(data, Map.class);
    Assert.assertEquals(om.writeValueAsString(wanted), om.writeValueAsString(actual));
  }

  private byte[] runTest(
      final String definition, final String metricInput, final RowsetProcessorFacotry factory)
      throws IOException, ExporterException, HttpException, ValidationException {
    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/" + definition));
    return runTest(conf, metricInput, factory);
  }

  private byte[] runTest(
      final Config conf, final String metricInput, final RowsetProcessorFacotry factory)
      throws IOException, ExporterException, HttpException, ValidationException {

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final RowMetadata meta =
        conf.isAllMetrics()
            ? new RowMetadata(conf, dataProvider.getStatKeysForResource(VM_ID_1))
            : new RowMetadata(conf);
    final StatsProcessor sp =
        new StatsProcessor(conf, meta, dataProvider, new LRUCache<>(1000), null, true);
    final RowsetProcessor rp = factory.makeFromConfig(out, conf, dataProvider);
    rp.preamble(meta, conf);
    sp.process(new FileInputStream("src/test/resources/" + metricInput + ".json"), rp, START, END);
    rp.close();
    return out.toByteArray();
  }
}
