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
package com.vmware.vropsexport;

import java.util.BitSet;
import java.util.NoSuchElementException;

@SuppressWarnings("WeakerAccess")
public class Row {
  public final int FIRST_METRIC_OFFSET = 2;

  private final long timestamp;

  private final BitSet definedMetrics;

  private final double[] metrics;

  private final String[] props;

  public Row(final long timestamp, final int nMetrics, final int nProps) {
    super();
    this.timestamp = timestamp;
    metrics = new double[nMetrics];
    props = new String[nProps];
    definedMetrics = new BitSet(nMetrics);
  }

  public long getTimestamp() {
    return timestamp;
  }

  public Double getMetric(final int i) {
    return definedMetrics.get(i) ? metrics[i] : null;
  }

  public String getProp(final int i) {
    return props[i];
  }

  public void setMetric(final int i, final double m) {
    metrics[i] = m;
    definedMetrics.set(i);
  }

  public void setProp(final int i, final String prop) {
    props[i] = prop;
  }

  public java.util.Iterator<Object> iterator(final RowMetadata meta) {
    return new Iterator(meta);
  }

  public int getNumProps() {
    return props.length;
  }

  public int getNumMetrics() {
    return metrics.length;
  }

  private class Iterator implements java.util.Iterator<Object> {
    private int mc;

    private int pc;

    private final RowMetadata meta;

    public Iterator(final RowMetadata meta) {
      this.meta = meta;
    }

    @Override
    public boolean hasNext() {
      return pc < props.length || mc < metrics.length;
    }

    @Override
    public Object next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (pc < props.length && meta.getPropInsertionPoints()[pc] == mc) {
        return getProp(pc++);
      }
      return getMetric(mc++);
    }
  }

  public Object[] flatten(final RowMetadata meta) {
    final Object[] answer = new Object[FIRST_METRIC_OFFSET + metrics.length + props.length];
    int i = 0;
    answer[0] = timestamp;
    i = FIRST_METRIC_OFFSET;
    for (final java.util.Iterator<Object> itor = iterator(meta); itor.hasNext(); ) {
      answer[i++] = itor.next();
    }
    return answer;
  }

  public void merge(final Row r) {
    // Merge metrics
    //
    for (int i = 0; i < r.getNumMetrics(); ++i) {
      final Double d = r.getMetric(i);
      if (d != null) {
        setMetric(i, d); // Don't be tempted to just set metrics[i]! Won't update the bitmap.
      }
    }

    // Merge properties
    //
    for (int i = 0; i < r.getNumProps(); ++i) {
      final String p = r.getProp(i);
      if (p != null) {
        props[i] = p;
      }
    }
  }
}
