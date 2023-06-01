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
import com.vmware.vropsexport.utils.IntKeyMap;
import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RowMetadata {
  public static class FieldSpec {
    private int index;
    private Field field;

    public FieldSpec(final int index, final Field field) {
      this.index = index;
      this.field = field;
    }

    public int getIndex() {
      return index;
    }

    public void setIndex(final int index) {
      this.index = index;
    }

    public Field getField() {
      return field;
    }

    public void setField(final Field field) {
      this.field = field;
    }

    public FieldSpec stripRelationships() {
      return new FieldSpec(
          index,
          new Field(
              field.getAlias(),
              field.getLocalName(),
              field.getKind(),
              null,
              null,
              Field.RelationshipType.self));
    }
  }

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

  private static final FieldSpec nullField = new FieldSpec(-1, null);

  private final String resourceKind;

  private final String adapterKind;

  private final ListValuedMap<String, FieldSpec> metricMap = new ArrayListValuedHashMap<>();

  private final List<Field> fields;

  private final Map<String, FieldSpec> propMap = new HashMap<>();

  private final Map<String, String> propNameToAlias = new HashMap<>();

  private final Map<String, Integer> metricAliasMap = new HashMap<>();

  private final IntKeyMap<String> metricIndexToAlias = new IntKeyMap<>();

  private final Map<String, Integer> propAliasMap = new HashMap<>();

  private final int[] propInsertionPoints;

  public RowMetadata(final Config conf, final List<String> metricNames) throws ExporterException {
    resourceKind = conf.getResourceKind();
    adapterKind = conf.getAdapterKind();
    final NameSanitizer ns = conf.createNameSanitizer();
    int mp = 0;
    for (final String metricName : metricNames) {
      metricMap.put(
          metricName,
          new FieldSpec(
              mp,
              new Field(
                  metricName,
                  metricName,
                  Field.Kind.metric,
                  null,
                  null,
                  Field.RelationshipType.self)));
      metricAliasMap.put(metricName, mp);
      metricIndexToAlias.put(mp, ns.transform(metricName));
      mp++;
    }
    propInsertionPoints = new int[0];
  }

  public int getNumMetrics() {
    return metricMap.size();
  }

  public RowMetadata(final Config conf) throws ExporterException {
    resourceKind = conf.getResourceKind();
    adapterKind = conf.getAdapterKind();
    int mp = 0;
    int pp = 0;
    final List<Integer> pip = new ArrayList<>();
    for (final Field fld : conf.getFields()) {
      if (fld.hasMetric()) {
        final String metricKey = fld.getMetric();
        if (metricMap.containsKey(metricKey)) {
          throw new ExporterException(
              "Repeated metrics are not supported. Offending metric: " + metricKey);
        }
        metricMap.put(metricKey, new FieldSpec(mp, fld));
        metricAliasMap.put(fld.getAlias(), mp);
        metricIndexToAlias.put(mp, fld.getAlias());
        mp++;
      } else {
        if (fld.hasProp()) {
          final String propKey = fld.getProp();
          if (metricMap.containsKey(propKey)) {
            throw new ExporterException(
                "Repeated properties are not supported. Offending property: " + propKey);
          }
          propMap.put(fld.getProp(), new FieldSpec(pp, fld));
          propAliasMap.put(fld.getAlias(), pp);
          propNameToAlias.put(fld.getProp(), fld.getAlias());
          pp++;
          pip.add(mp);
        }
      }
    }
    propInsertionPoints = new int[pip.size()];
    for (int i = 0; i < pip.size(); ++i) {
      propInsertionPoints[i] = pip.get(i);
    }
  }

  private RowMetadata(
      final RowMetadata origin, final String resourceKind, final String adapterKind) {
    propInsertionPoints = origin.propInsertionPoints;
    for (final Map.Entry<String, FieldSpec> e : origin.getPropMap().entrySet()) {
      final FieldSpec fs = e.getValue();
      final Field f = fs.getField();
      if (f.isRelatedTo(adapterKind, resourceKind)) {
        propMap.put(f.getLocalName(), fs.stripRelationships());
      } else {
        propMap.put("_placeholder_" + f.getName(), fs.stripRelationships());
      }
    }
    for (final Map.Entry<String, FieldSpec> e : origin.metricMap.entries()) {
      final FieldSpec fs = e.getValue();
      final Field f = fs.getField();
      if (f.isRelatedTo(adapterKind, resourceKind)) {
        metricMap.put(f.getLocalName(), fs.stripRelationships());
      } else {
        metricMap.put("_placeholder_" + f.getName(), fs.stripRelationships());
      }
    }
    this.resourceKind = resourceKind;
    this.adapterKind = adapterKind;
  }

  public Map<RelationshipSpec, RowMetadata> forRelated() {
    final Stream<RelationshipSpec> relations =
        Stream.concat(propMap.values().stream(), metricMap.values().stream())
            .filter((p) -> p.field.getRelationshipType() != Field.RelationshipType.self)
            .map(
                (p) ->
                    new RelationshipSpec(
                        p.field.getRelationshipType(),
                        p.field.getRelatedAdapterKind(),
                        p.field.getRelatedResourceKind(),
                        p.field.getSearchDepth()))
            .distinct();
    final Map<RelationshipSpec, RowMetadata> result = new HashMap<>();
    for (final Iterator<RelationshipSpec> itor = relations.iterator(); itor.hasNext(); ) {
      final RelationshipSpec rs = itor.next();
      result.put(rs, new RowMetadata(this, rs.resourceKind, rs.adapterKind));
    }
    return result;
  }

  public ListValuedMap<String, FieldSpec> getMetricMap() {
    return metricMap;
  }

  public Map<String, FieldSpec> getPropMap() {
    return propMap;
  }

  public int[] getPropInsertionPoints() {
    return propInsertionPoints;
  }

  public List<Integer> getMetricIndex(final String metric) {
    final List<FieldSpec> specs = metricMap.get(metric);
    if (specs == null) {
      return Collections.emptyList();
    }
    return specs.stream().map(FieldSpec::getIndex).collect(Collectors.toList());
  }

  public int getPropertyIndex(final String property) {
    return propMap.getOrDefault(property, nullField).index;
  }

  public int getTagIndex(final String tag) {
    return getPropertyIndex(Field.TAG_PROP_PREFIX + tag);
  }

  public int getMetricIndexByAlias(final String metric) {
    return metricAliasMap.getOrDefault(metric, -1);
  }

  public int getPropertyIndexByAlias(final String property) {
    return propAliasMap.getOrDefault(property, -1);
  }

  public String getAliasForProp(final String name) {
    return propNameToAlias.get(name);
  }

  public String getAliasForMetricIndex(final int index) {
    return metricIndexToAlias.get(index);
  }

  public Row newRow(final long timestamp) {
    return new Row(timestamp, metricMap.size(), propMap.size());
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
    final Aggregator[] aggs = new Aggregator[metricMap.size()];
    for (final FieldSpec fs : metricMap.values()) {
      if (fs.getField().getRelationshipType() != Field.RelationshipType.self) {
        aggs[fs.index] = Aggregators.forType(fs.field.getAggregation());
      }
    }
    return aggs;
  }

  public boolean hasDeclaredRelationships() {
    for (final FieldSpec fs : metricMap.values()) {
      if (fs.getField().getRelationshipType() != Field.RelationshipType.self) {
        return true;
      }
    }
    for (final FieldSpec fs : propMap.values()) {
      if (fs.getField().getRelationshipType() != Field.RelationshipType.self) {
        return true;
      }
    }
    return false;
  }
}
