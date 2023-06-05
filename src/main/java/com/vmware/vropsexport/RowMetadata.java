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
package com.vmware.vropsexport;

import com.vmware.vropsexport.exceptions.ExporterException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RowMetadata {

  public static class RelationshipSpec {
    private String adapterKind;
    private String resourceKind;
    private final int searchDepth;
    private Field.RelationshipType type;

    public RelationshipSpec(
        final Field.RelationshipType type,
        final String adapterKind,
        final String resourceKind,
        final int searchDepth) {
      this.type = type;
      this.adapterKind = adapterKind;
      this.resourceKind = resourceKind;
      this.searchDepth = searchDepth;
    }

    public String getAdapterKind() {
      return adapterKind;
    }

    public void setAdapterKind(final String adapterKind) {
      this.adapterKind = adapterKind;
    }

    public String getResourceKind() {
      return resourceKind;
    }

    public void setResourceKind(final String resourceKind) {
      this.resourceKind = resourceKind;
    }

    public Field.RelationshipType getType() {
      return type;
    }

    public void setType(final Field.RelationshipType type) {
      this.type = type;
    }

    public int getSearchDepth() {
      return searchDepth;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final RelationshipSpec that = (RelationshipSpec) o;
      return searchDepth == that.searchDepth
          && Objects.equals(adapterKind, that.adapterKind)
          && Objects.equals(resourceKind, that.resourceKind)
          && type == that.type;
    }

    @Override
    public int hashCode() {
      return Objects.hash(adapterKind, resourceKind, searchDepth, type);
    }
  }

  private final String resourceKind;

  private final String adapterKind;

  private final List<Field> fields;

  /** Property name to Field */
  private final Map<String, Field> propMap = new HashMap<>();

  /** Metric alias to index in the fields list */
  private final Map<String, Field> aliasToField = new HashMap<>();

  public RowMetadata(final Config conf, final List<String> metricNames) throws ExporterException {
    resourceKind = conf.getResourceKind();
    adapterKind = conf.getAdapterKind();
    fields = new ArrayList<>();
    final NameSanitizer ns = conf.createNameSanitizer();
    for (final String metricName : metricNames) {
      final Field f =
          new Field(
              metricName,
              ns.transform(metricName),
              Field.Kind.metric,
              null,
              null,
              Field.RelationshipType.self);
      fields.add(f);
      aliasToField.put(metricName, f);
    }
    initialize();
  }

  public RowMetadata(final Config conf) throws ExporterException {
    resourceKind = conf.getResourceKind();
    adapterKind = conf.getAdapterKind();
    fields = conf.getFields();
    for (final Field f : conf.getFields()) {
      aliasToField.put(f.getAlias(), f);
      if (f.hasProp()) {
        propMap.put(f.getProp(), f);
      }
    }
    initialize();
  }

  private RowMetadata(
      final RowMetadata origin, final String resourceKind, final String adapterKind) {
    fields =
        origin.fields.stream()
            .map(
                (f) ->
                    new Field(
                        f.getAlias(),
                        f.getLocalName(),
                        f.getKind(),
                        null,
                        null,
                        Field.RelationshipType.self))
            .collect(Collectors.toList());
    for (final Field f : fields) {
      if (f.isRelatedTo(adapterKind, resourceKind)) {
        propMap.put(f.getLocalName(), f);
      }
    }
    this.resourceKind = resourceKind;
    this.adapterKind = adapterKind;
    initialize();
  }

  private void initialize() {
    int mi = 0;
    int pi = 0;
    for (final Field f : fields) {
      if (f.hasMetric()) {
        f.setRowIndex(mi++);
      } else {
        f.setRowIndex(pi++);
      }
    }
  }

  private int getNumMetrics() {
    return (int) fields.stream().filter(Field::hasMetric).count();
  }

  public List<Field> getFields() {
    return fields;
  }

  public Map<RelationshipSpec, RowMetadata> forRelated() {
    final Stream<RelationshipSpec> relations =
        fields.stream()
            .filter((f) -> f.getRelationshipType() != Field.RelationshipType.self)
            .map(
                (f) ->
                    new RelationshipSpec(
                        f.getRelationshipType(),
                        f.getRelatedAdapterKind(),
                        f.getRelatedResourceKind(),
                        f.getSearchDepth()))
            .distinct();
    final Map<RelationshipSpec, RowMetadata> result = new HashMap<>();
    for (final Iterator<RelationshipSpec> itor = relations.iterator(); itor.hasNext(); ) {
      final RelationshipSpec rs = itor.next();
      result.put(rs, new RowMetadata(this, rs.resourceKind, rs.adapterKind));
    }
    return result;
  }

  public Map<String, Field> getPropMap() {
    return propMap;
  }

  public List<Field> getMetrics() {
    return fields.stream().filter(Field::hasMetric).collect(Collectors.toList());
  }

  /*

  public int getTagIndex(final String tag) {
    return getPropertyIndex(Field.TAG_PROP_PREFIX + tag); // TODO: We probably need this!
  }
  */

  public Field getFieldByAlias(final String name) {
    return aliasToField.get(name);
  }

  public String getAliasForProp(final String name) {
    return propMap.get(name).getAlias();
  }

  public Row newRow(final long timestamp) {
    return new Row(timestamp, getNumMetrics(), propMap.size());
  }

  public String getResourceKind() {
    return resourceKind;
  }

  public String getAdapterKind() {
    return adapterKind;
  }

  public boolean hasProperties() {
    return propMap.size() > 0;
  }

  public Field getProperty(final String name) {
    return propMap.get(name);
  }

  public Iterable<Field> getPropertiesIterable() {
    return fields.stream().filter(Field::hasProp)::iterator;
  }

  public Iterable<Field> getMetricsIterable() {
    return fields.stream().filter(Field::hasMetric)::iterator;
  }

  public boolean needsPropertyLoad() {
    if (!hasProperties()) {
      return false;
    }
    for (final String key : propMap.keySet()) {
      if (!(key.equals("$resId") || key.equals("$resName"))) {
        return true;
      }
    }
    return false;
  }

  public Aggregator[] createAggregators() {
    final Aggregator[] aggs = new Aggregator[getNumMetrics()];
    int i = 0;
    for (final Field f : getMetricsIterable()) {
      if (f.getRelationshipType() != Field.RelationshipType.self) {
        aggs[i] = Aggregators.forType(f.getAggregation());
      }
      ++i;
    }
    return aggs;
  }

  public boolean hasDeclaredRelationships() {
    for (final Field f : fields) {
      if (f.getRelationshipType() != Field.RelationshipType.self) {
        return true;
      }
    }
    return false;
  }
}
