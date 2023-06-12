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

import java.util.Objects;
import java.util.regex.Matcher;

public class Field {
  public enum Kind {
    metric,
    property,
    tag
  }

  public enum RelationshipType {
    self,
    child,
    parent,
  }

  public enum AggregationType {
    none,
    sum,
    max,
    min,
    avg,
    stddev,
    median,
    variance,
    first,
    last
  }

  protected static final String TAG_PROP_PREFIX = "summary|tagJson#";

  private String alias;
  private String name;
  private String localName;
  private String relatedResourceKind;
  private String relatedAdapterKind = "VMWARE";
  private RelationshipType relationshipType = RelationshipType.self;
  private Kind kind;
  private int searchDepth = 1;
  private AggregationType aggregation = AggregationType.avg;
  private int rowIndex = -1;

  public Field() {}

  public Field(
      final String alias,
      final String name,
      final Kind kind,
      final String relatedAdapterKind,
      final String relatedResourceKind,
      final RelationshipType relationshipType) {
    super();
    this.alias = alias;
    this.relatedResourceKind = relatedResourceKind;
    this.relationshipType = relationshipType;
    this.relatedAdapterKind = relatedAdapterKind;
    switch (kind) {
      case metric:
        setMetric(name);
        break;
      case property:
        setProp(name);
        break;
      case tag:
        setTag(name);
        break;
    }
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
    final Matcher m = Patterns.relativePattern.matcher(name);
    if (m.matches()) {
      relationshipType = RelationshipType.valueOf(m.group(1));
      localName = m.group(3);
      relatedResourceKind = m.group(2);
    } else {
      localName = name;
    }
  }

  void setRowIndex(final int i) {
    rowIndex = i;
  }

  public int getRowIndex() {
    return rowIndex;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(final String alias) {
    this.alias = alias;
  }

  public String getMetric() {
    return hasMetric() ? name : null;
  }

  public boolean hasMetric() {
    return kind == Kind.metric;
  }

  public void setMetric(final String metric) {
    setName(metric);
    kind = Kind.metric;
  }

  public String getProp() {
    return hasProp() ? name : null;
  }

  public void setProp(final String prop) {
    setName(prop);
    kind = Kind.property;
  }

  public void setTag(final String tag) {
    setName(TAG_PROP_PREFIX + tag);
    kind = Kind.tag;
  }

  public boolean hasProp() {
    return kind == Kind.property;
  }

  public String getLocalName() {
    return localName;
  }

  public void setLocalName(final String localName) {
    this.localName = localName;
  }

  public String getRelatedResourceKind() {
    return relatedResourceKind;
  }

  public void setRelatedResourceKind(final String relatedResourceKind) {
    this.relatedResourceKind = relatedResourceKind;
  }

  public RelationshipType getRelationshipType() {
    return relationshipType;
  }

  public void setRelationshipType(final RelationshipType relationshipType) {
    this.relationshipType = relationshipType;
  }

  public Kind getKind() {
    return kind;
  }

  public void setKind(final Kind kind) {
    this.kind = kind;
  }

  public String getRelatedAdapterKind() {
    return relatedAdapterKind;
  }

  public void setRelatedAdapterKind(final String relatedAdapterKind) {
    this.relatedAdapterKind = relatedAdapterKind;
  }

  public int getSearchDepth() {
    return searchDepth;
  }

  public void setSearchDepth(final int searchDepth) {
    this.searchDepth = searchDepth;
  }

  public AggregationType getAggregation() {
    return aggregation;
  }

  public void setAggregation(final AggregationType aggregation) {
    this.aggregation = aggregation;
  }

  public boolean isRelatedTo(final String adapterKind, final String resourceKind) {
    return relationshipType != RelationshipType.self
        && Objects.equals(adapterKind, relatedAdapterKind)
        && Objects.equals(resourceKind, relatedResourceKind);
  }
}
