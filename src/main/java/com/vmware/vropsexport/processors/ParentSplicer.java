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

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.LRUCache;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;

public class ParentSplicer implements RowsetProcessor {

  private final Rowset childRowset;

  private final LRUCache<String, Rowset> rowsetCache;

  private final String cacheKey;

  public ParentSplicer(Rowset childRowset, LRUCache<String, Rowset> rowsetCache, String cacheKey) {
    super();
    this.childRowset = childRowset;
    this.rowsetCache = rowsetCache;
    this.cacheKey = cacheKey;
  }

  @Override
  public void preamble(RowMetadata meta, Config conf) throws ExporterException {
    // Nothing to do...
  }

  @Override
  public void process(Rowset rowset, RowMetadata meta) throws ExporterException {
    spliceRows(childRowset, rowset);
    synchronized (this.rowsetCache) {
      this.rowsetCache.put(cacheKey, rowset);
    }
  }

  @Override
  public void close() {
    // Nothing to do
  }

  public static void spliceRows(Rowset child, Rowset parent) {
    for (Row pRow : parent.getRows().values()) {
      Row cRow = child.getRows().get(pRow.getTimestamp());
      if (cRow != null) {
        for (int j = 0; j < pRow.getNumMetrics(); ++j) {
          Double d = pRow.getMetric(j);
          if (d != null) cRow.setMetric(j, d);
        }
        for (int j = 0; j < pRow.getNumProps(); ++j) {
          String s = pRow.getProp(j);
          if (s != null) cRow.setProp(j, s);
        }
      }
    }
  }
}
