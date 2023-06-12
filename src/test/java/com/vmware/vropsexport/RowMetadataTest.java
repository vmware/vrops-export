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

package com.vmware.vropsexport;

import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.exceptions.ValidationException;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RowMetadataTest {
  private List<String> parseList(final String s) {
    return Arrays.stream(s.split(",")).sorted().collect(Collectors.toList());
  }

  private void testRowIndices(final String confFile)
      throws ExporterException, FileNotFoundException, ValidationException {
    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/" + confFile));
    final RowMetadata meta = new RowMetadata(conf);
    int i = 0;
    for (final Field f : meta.getMetricsIterable()) {
      Assert.assertEquals(i++, f.getRowIndex());
    }
    i = 0;
    for (final Field f : meta.getPropertiesIterable()) {
      Assert.assertEquals(i++, f.getRowIndex());
    }
  }

  @Test
  public void testBasicRowIndices()
      throws ExporterException, FileNotFoundException, ValidationException {
    testRowIndices("basic.yaml");
  }

  @Test
  public void testChildRowIndices()
      throws ExporterException, FileNotFoundException, ValidationException {
    testRowIndices("children.yaml");
  }

  @Test
  public void testParent() throws ExporterException, FileNotFoundException, ValidationException {
    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/grandparent.yaml"));
    final RowMetadata meta = new RowMetadata(conf);
    final Map<RowMetadata.RelationshipSpec, RowMetadata> related = meta.forRelated();
    Assert.assertEquals(3, related.size());
    final List<String> adapterKinds = parseList("VMWARE,VMWARE,VMWARE");
    final List<String> resourceKinds = parseList("ClusterComputeResource,Datacenter,HostSystem");
  }

  @Test
  public void testChildren() throws FileNotFoundException, ExporterException, ValidationException {
    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/children.yaml"));
    final RowMetadata meta = new RowMetadata(conf);
    final Aggregator[] aggs = meta.createAggregators();
    Assert.assertNull(aggs[0]);
    Assert.assertNotNull(aggs[1]);
    Assert.assertEquals(Aggregators.Average.class, aggs[1].getClass());
  }
}
