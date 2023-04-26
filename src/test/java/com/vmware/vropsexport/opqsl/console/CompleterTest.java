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
package com.vmware.vropsexport.opqsl.console;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.Metadata;
import com.vmware.vropsexport.models.AdapterKind;
import com.vmware.vropsexport.models.AdapterKindResponse;
import com.vmware.vropsexport.models.ResourceAttributeResponse;
import com.vmware.vropsexport.opsql.Constants;
import com.vmware.vropsexport.opsql.console.Completer;
import com.vmware.vropsexport.opsql.console.Parser;
import org.apache.http.HttpException;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompleterTest {
  private final List<ResourceAttributeResponse.ResourceAttribute> stats;

  private final List<ResourceAttributeResponse.ResourceAttribute> props;

  private final List<AdapterKind> adapterKinds;

  private final List<String> keywords =
      Arrays.stream(Constants.keywords).collect(Collectors.toList());

  public CompleterTest() throws IOException {
    final ObjectMapper om =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    stats =
        om.readValue(
                new File("src/test/resources/vmstatkeys.json"), ResourceAttributeResponse.class)
            .getResourceAttributes();
    props =
        om.readValue(
                new File("src/test/resources/fullvmprops.json"), ResourceAttributeResponse.class)
            .getResourceAttributes();
    adapterKinds =
        om.readValue(
                new File("src/test/resources/opsql/AdapterKindResponse.json"),
                AdapterKindResponse.class)
            .getAdapterKind();
  }

  private void runTest(final String s, final int cursor, final List<String> expected)
      throws HttpException, IOException {
    final Metadata backend = mock(Metadata.class);
    when(backend.getAdapterKinds()).thenReturn(adapterKinds);
    when(backend.getStatKeysForResourceKind(any(), any())).thenReturn(stats);
    when(backend.getPropertyKeysForResourceKind(any(), any())).thenReturn(props);
    final Completer completer = new Completer(backend);
    final LineReader lineReader =
        LineReaderBuilder.builder().completer(completer).parser(new Parser()).build();
    final List<Candidate> candidates = new ArrayList<>();
    completer.complete(lineReader, new Parser().parse(s, cursor), candidates);
    Assert.assertEquals(
        expected, candidates.stream().map(Candidate::value).collect(Collectors.toList()));
  }

  @Test
  public void testKeywordExpansion() throws HttpException, IOException {
    runTest("res", 2, keywords);
  }

  @Test
  public void testMetricExpansion() throws HttpException, IOException {
    final List<String> expected = new ArrayList<>(keywords);
    expected.addAll(stats.stream().map((r) -> r.getKey()).collect(Collectors.toList()));
    runTest("resource(VMWARE:VirtualMachine).fields(cpu", 41, expected);
  }

  @Test
  public void testPropertyExpansion() throws HttpException, IOException {
    final List<String> expected = new ArrayList<>(keywords);
    expected.addAll(props.stream().map((r) -> "@" + r.getKey()).collect(Collectors.toList()));
    runTest("resource(VMWARE:VirtualMachine).fields(@summary", 41, expected);
  }
}
