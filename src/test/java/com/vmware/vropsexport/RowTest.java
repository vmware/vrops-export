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
import java.util.Iterator;

public class RowTest {
  public void testRow(final String config, final int nMetrics, final int nProps)
      throws ExporterException, FileNotFoundException, ValidationException {
    final Config conf = ConfigLoader.parse(new FileReader("src/test/resources/" + config));
    final RowMetadata meta = new RowMetadata(conf);
    final Row row = meta.newRow(0L);
    for (final Field f : meta.getFields()) {
      if (f.hasMetric()) {
        row.setMetric(f.getRowIndex(), f.getName().hashCode());
      } else {
        row.setProp(f.getRowIndex(), f.getName());
      }
    }
    Assert.assertEquals(nMetrics, row.getNumMetrics());
    Assert.assertEquals(nProps, row.getNumProps());
    int i = 0;
    for (final Iterator<Object> itor = row.iterator(meta); itor.hasNext(); ) {
      final Object value = itor.next();
      if (value instanceof Number) {
        Assert.assertEquals(row.getMetric(meta.getFields().get(i++).getRowIndex()), value);
      } else {
        Assert.assertEquals(row.getProp(meta.getFields().get(i++).getRowIndex()), value);
      }
    }
    Assert.assertEquals(nMetrics + nProps, i);
  }

  @Test
  public void testBasicRow() throws ExporterException, FileNotFoundException, ValidationException {
    testRow("basic.yaml", 6, 3);
  }

  @Test
  public void testGrandparentRow()
      throws ExporterException, FileNotFoundException, ValidationException {
    testRow("grandparent.yaml", 12, 6);
  }
}
