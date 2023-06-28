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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AggregatorTest {
  private Aggregator fillWithSequence(final Aggregator agg, final int n) {
    Assert.assertFalse(agg.hasResult());
    for (int i = 0; i < n; ++i) {
      agg.apply(i);
    }
    Assert.assertTrue(agg.hasResult());
    return agg;
  }

  @Test
  public void testSum() {
    Assert.assertEquals(4950, fillWithSequence(new Aggregators.Sum(), 100).getResult(), 0.00001);
  }

  @Test
  public void testAverage() {
    Assert.assertEquals(
        49.5, fillWithSequence(new Aggregators.Average(), 100).getResult(), 0.00001);
  }

  @Test
  public void testMedian() {
    Assert.assertEquals(49.5, fillWithSequence(new Aggregators.Median(), 100).getResult(), 0.00001);
    Assert.assertEquals(50, fillWithSequence(new Aggregators.Median(), 101).getResult(), 0.00001);
    Assert.assertEquals(1, fillWithSequence(new Aggregators.Median(), 3).getResult(), 0.00001);
    final List<Double> values = new ArrayList<>();
    for (int i = 0; i < 100; ++i) {
      values.add((double) i);
    }
    Collections.shuffle(values);
    final Aggregator agg = new Aggregators.Median();
    for (final double v : values) {
      agg.apply(v);
    }
    Assert.assertEquals(49.5, agg.getResult(), 0.00001);
  }

  @Test
  public void testFirst() {
    Assert.assertEquals(0, fillWithSequence(new Aggregators.First(), 100).getResult(), 0.00001);
  }

  @Test
  public void testStddev() {
    Assert.assertEquals(
        29.01149198, fillWithSequence(new Aggregators.StdDev(), 100).getResult(), 0.00001);
  }

  @Test
  public void testLast() {
    Assert.assertEquals(99, fillWithSequence(new Aggregators.Last(), 100).getResult(), 0.00001);
  }

  private void assertNaN(final Aggregator agg) {
    Assert.assertFalse(agg.hasResult());
    Assert.assertTrue(Double.isNaN(agg.getResult()));
  }

  private void assertZero(final Aggregator agg) {
    Assert.assertFalse(agg.hasResult());
    Assert.assertEquals(0, agg.getResult(), 0);
  }

  @Test
  public void testEmpty() {
    assertNaN(new Aggregators.Average());
    assertNaN(new Aggregators.Min());
    assertNaN(new Aggregators.Max());
    assertNaN(new Aggregators.First());
    assertNaN(new Aggregators.Last());
    assertNaN(new Aggregators.StdDev());
    assertNaN(new Aggregators.Median());
    assertNaN(new Aggregators.Variance());
    assertZero(new Aggregators.Sum());
  }
}
