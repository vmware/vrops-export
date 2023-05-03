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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Aggregators {
  private static final Map<Field.AggregationType, AggregatorFactory> nameLookup = new HashMap<>();

  private interface AggregatorFactory {
    Aggregator create();
  }

  static {
    nameLookup.put(Field.AggregationType.sum, Sum::new);
    nameLookup.put(Field.AggregationType.avg, Average::new);
    nameLookup.put(Field.AggregationType.min, Min::new);
    nameLookup.put(Field.AggregationType.max, Max::new);
    nameLookup.put(Field.AggregationType.median, Median::new);
    nameLookup.put(Field.AggregationType.variance, Variance::new);
    nameLookup.put(Field.AggregationType.stddev, StdDev::new);
    nameLookup.put(Field.AggregationType.first, First::new);
    nameLookup.put(Field.AggregationType.last, Last::new);
  }

  public static Aggregator forType(final Field.AggregationType type) {
    return nameLookup.get(type).create();
  }

  public static class Average implements Aggregator {
    private int count;

    private double sum;

    @Override
    public void apply(final double v) {
      count++;
      sum += v;
    }

    @Override
    public double getResult() {
      return sum / (double) count;
    }
  }

  public static class Sum implements Aggregator {
    private double sum;

    @Override
    public void apply(final double v) {
      sum += v;
    }

    @Override
    public double getResult() {
      return sum;
    }
  }

  public static class Min implements Aggregator {
    private double min = Double.POSITIVE_INFINITY;

    @Override
    public void apply(final double v) {
      if (v < min) {
        min = v;
      }
    }

    @Override
    public double getResult() {
      return min;
    }
  }

  public static class Max implements Aggregator {
    private double max = Double.NEGATIVE_INFINITY;

    @Override
    public void apply(final double v) {
      if (v < max) {
        max = v;
      }
    }

    @Override
    public double getResult() {
      return max;
    }
  }

  public static class Median implements Aggregator {
    List<Double> values = new ArrayList<>();

    @Override
    public void apply(final double v) {
      values.add(v);
    }

    @Override
    public double getResult() {
      values.sort(Double::compareTo);
      return values.get(values.size() / 2);
    }
  }

  public static class Variance implements Aggregator {
    private double vAcc;

    private double sum;

    private double avg;

    private int count;

    @Override
    public void apply(final double v) {
      ++count;
      sum += v;
      final double a = sum / (double) count;
      vAcc += (v - avg) * (v - a);
      avg = a;
    }

    @Override
    public double getResult() {
      return count > 1 ? vAcc / (count - 1) : 0;
    }
  }

  public static class StdDev extends Aggregators.Variance {
    @Override
    public double getResult() {
      return Math.sqrt(super.getResult());
    }
  }

  public static class First implements Aggregator {
    private Double first;

    @Override
    public void apply(final double v) {
      if (first == null) {
        first = v;
      }
    }

    @Override
    public double getResult() {
      return first;
    }
  }

  public static class Last implements Aggregator {
    private double last;

    @Override
    public void apply(final double v) {
      last = v;
    }

    @Override
    public double getResult() {
      return last;
    }
  }
}
