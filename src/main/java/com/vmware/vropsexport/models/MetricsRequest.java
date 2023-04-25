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
package com.vmware.vropsexport.models;

import java.util.List;

public class MetricsRequest {

  private final List<String> resourceId;

  private final boolean currentOnly;

  private final String rollUpType;

  private final String intervalType;

  private final int intervalQuantifier;

  private final Integer maxSamples;

  private final Long begin;

  private final Long end;

  private final List<String> stat;

  public MetricsRequest(
      final List<String> resourceId,
      final boolean currentOnly,
      final String rollupType,
      final String intervalType,
      final int intervalQuantifier,
      final Integer maxSamples,
      final Long begin,
      final Long end,
      final List<String> stat) {
    this.resourceId = resourceId;
    this.currentOnly = currentOnly;
    this.rollUpType = rollupType;
    this.intervalType = intervalType;
    this.maxSamples = maxSamples;
    this.begin = begin;
    this.end = end;
    this.stat = stat;
    this.intervalQuantifier = intervalQuantifier;
  }

  public List<String> getResourceId() {
    return resourceId;
  }

  public boolean isCurrentOnly() {
    return currentOnly;
  }

  public String getRollUpType() {
    return rollUpType;
  }

  public String getIntervalType() {
    return intervalType;
  }

  public Integer getMaxSamples() {
    return maxSamples;
  }

  public List<String> getStat() {
    return stat;
  }

  public Long getBegin() {
    return begin;
  }

  public Long getEnd() {
    return end;
  }

  public int getIntervalQuantifier() {
    return intervalQuantifier;
  }
}
