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

import com.vmware.vropsexport.*;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.utils.LRUCache;

import java.util.HashMap;
import java.util.Map;

public class RowSplicer implements RowsetProcessor {

  private final Rowset originRowset;

  private final LRUCache<String, Rowset> rowsetCache;

  private String cacheKey;

  private final Map<Long, Aggregator[]> aggregators;

  private final RowMetadata metadata;

  public RowSplicer(
      final Rowset childRowset,
      final RowMetadata metadata,
      final LRUCache<String, Rowset> rowsetCache) {
    super();
    originRowset = childRowset;
    this.rowsetCache = rowsetCache;
    aggregators = new HashMap<>();
    this.metadata = metadata;
  }

  public void setCacheKey(final String cacheKey) {
    this.cacheKey = cacheKey;
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) {
    // Nothing to do...
  }

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
    spliceRows(originRowset, rowset);
    synchronized (rowsetCache) {
      rowsetCache.put(cacheKey, rowset);
    }
  }

  @Override
  public void close() {
    // Nothing to do
  }

  public void spliceRows(final Rowset origin, final Rowset related) {

    for (final Row pRow : related.getRows().values()) {
      final Row cRow = origin.getRows().get(pRow.getTimestamp());
      if (cRow != null) {
        final Aggregator[] aggs =
            aggregators.computeIfAbsent(cRow.getTimestamp(), (t) -> metadata.createAggregators());
        for (int j = 0; j < pRow.getNumMetrics(); ++j) {
          final Double d = pRow.getMetric(j);
          if (d != null) {
            aggs[j].apply(d);
          }
        }
        for (int j = 0; j < pRow.getNumProps(); ++j) {
          final String s = pRow.getProp(j);
          if (s != null) {
            cRow.setProp(j, s);
          }
        }
      }
    }
  }

  public void finish(final Rowset origin) {
    for (final Row row : origin.getRows().values()) {
      final Aggregator[] aggs = aggregators.get(row.getTimestamp());
      if (aggs == null) {
        continue;
      }
      for (int j = 0; j < row.getNumMetrics(); ++j) {
        final Aggregator agg = aggs[j];
        if (agg != null) {
          row.setMetric(j, agg.getResult());
        }
      }
    }
  }
}
