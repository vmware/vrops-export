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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.models.NamedResource;
import com.vmware.vropsexport.processors.RowSplicer;
import com.vmware.vropsexport.utils.IndexedLocks;
import com.vmware.vropsexport.utils.LRUCache;
import org.apache.http.HttpException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@SuppressWarnings("SameParameterValue")
public class StatsProcessor {
  private static class NullProgress implements ProgressMonitor {
    @Override
    public void reportProgress(final int n) {}
  }

  private static final Logger log = LogManager.getLogger(StatsProcessor.class);

  private final Config conf;

  private final RowMetadata rowMetadata;

  private final DataProvider dataProvider;

  private final LRUCache<String, Rowset> rowsetCache;

  private final boolean verbose;

  private final ProgressMonitor pm;

  public StatsProcessor(
      final Config conf,
      final RowMetadata rowMetadata,
      final DataProvider propertyProvider,
      final LRUCache<String, Rowset> rowsetCache,
      final ProgressMonitor pm,
      final boolean verbose) {
    this.conf = conf;
    this.rowMetadata = rowMetadata;
    dataProvider = propertyProvider;
    this.rowsetCache = rowsetCache;
    this.verbose = verbose;
    this.pm = pm;
  }

  public int process(
      final InputStream is, final RowsetProcessor proc, final long begin, final long end)
      throws ExporterException, IOException, HttpException {
    RowMetadata meta = rowMetadata;
    final JsonParser p = new JsonFactory().createParser(is);
    int processedObjects = 0;

    // Process values { [ ...
    //
    expect(p, JsonToken.START_OBJECT);
    expect(p, "values");
    expect(p, JsonToken.START_ARRAY);
    while (p.nextToken() != JsonToken.END_ARRAY) {
      expectCurrent(p, JsonToken.START_OBJECT);
      expect(p, "resourceId");
      final String resourceId = p.nextTextValue();

      // Looking for all metrics? We need to create metadata for each resource, since instance
      // metrics may very.
      if (conf.isAllMetrics()) {
        meta = new RowMetadata(conf, dataProvider.getStatKeysForResource(resourceId));
      }

      // Process stat-list { stat [ ...
      expect(p, "stat-list");
      expect(p, JsonToken.START_OBJECT);
      expect(p, "stat");
      expect(p, JsonToken.START_ARRAY);
      TreeMap<Long, Row> rows = new TreeMap<>();
      while (p.nextToken() != JsonToken.END_ARRAY) {

        // Process timestamps[ ...
        expectCurrent(p, JsonToken.START_OBJECT);
        expect(p, "timestamps");
        expect(p, JsonToken.START_ARRAY);
        final List<Long> timestamps = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_ARRAY) {
          long ts = p.getLongValue();

          // Align timestamp if needed
          final int align = conf.getAlign() * 1000;
          if (align != 0) {
            ts = ((ts + align / 2) / align) * align;
          }
          timestamps.add(ts);
        }
        expect(p, "statKey");
        expect(p, JsonToken.START_OBJECT);
        expect(p, "key");
        final String statKey = p.nextTextValue();
        expect(p, JsonToken.END_OBJECT);

        // Keep skipping members until we've found the data node
        while (!expectMaybe(p, "data")) {
          skipMember(p, null);
        }

        // Process data[ ...
        expect(p, JsonToken.START_ARRAY);
        int ti = 0;
        while (p.nextToken() != JsonToken.END_ARRAY) {
          if (ti >= timestamps.size()) {
            log.warn(
                "More data than timestamps (index="
                    + ti
                    + ") for metric "
                    + statKey
                    + " on "
                    + meta.getResourceKind()
                    + " id: "
                    + resourceId);
            continue; // Skip this sample!
          }
          final double d = p.getDoubleValue();
          final long ts = timestamps.get(ti);

          // Enter the value on every field that matches kind and name
          int mi = 0;
          for (final Field f : meta.getFields()) {
            if (!(f.hasMetric() && f.getLocalName().equals(statKey))) {
              continue;
            }
            final RowMetadata m = meta;
            final Row r = rows.computeIfAbsent(ts, k -> m.newRow(ts));
            r.setMetric(meta.getMetricIndex(mi), d);
            ++mi;
          }
          ++ti;
        }
        expect(p, JsonToken.END_OBJECT);
      }

      // End of stat-list and values object
      expect(p, JsonToken.END_OBJECT);
      expect(p, JsonToken.END_OBJECT);
      Rowset rs = new Rowset(resourceId, rows);
      rows = null; // Make the GC release this a bit earlier

      // Splice in properties
      if (dataProvider != null) {
        if (meta.hasProperties()) {
          // Put in resource id if requested.
          final int idIdx = meta.getPropertyIndex("$resId");
          if (idIdx != -1) {
            for (final Row row : rs.getRows().values()) {
              row.setProp(idIdx, resourceId);
            }
          }

          // Put in name if requested
          final int nameIdx = meta.getPropertyIndex("$resName");
          if (nameIdx != -1) {
            final String name = dataProvider.getResourceName(resourceId);
            for (final Row row : rs.getRows().values()) {
              row.setProp(nameIdx, name);
            }
          }

          // Splice in properties
          if (meta.needsPropertyLoad()) {
            final Map<String, String> props = dataProvider.fetchProps(resourceId);
            for (final Map.Entry<String, String> e : props.entrySet()) {
              final int idx = meta.getPropertyIndex(e.getKey());
              if (idx != -1) {
                for (final Row row : rs.getRows().values()) {
                  row.setProp(idx, e.getValue());
                }
              }
            }
            // Splice in tags
            final String tags = props.get("summary|tagJson");
            if (tags != null && !"none".equals(tags)) {
              final ObjectMapper om = new ObjectMapper();
              final List<Map<String, String>> parsed =
                  om.readValue(tags, new TypeReference<List<Map<String, String>>>() {});
              for (final Map<String, String> tag : parsed) {
                final int idx = meta.getPropertyIndex(tag.get("category"));
                if (idx != -1) {
                  final String tagValue = tag.get("name");
                  for (final Row row : rs.getRows().values()) {
                    row.setProp(idx, tagValue);
                  }
                }
              }
            }
          }
        }

        // Splice in data from parent
        if (meta.hasDeclaredRelationships()) {
          spliceInRelative(resourceId, meta, rs, begin, end);
        }
      }
      if (verbose) {
        log.debug(
            "Processed "
                + rs.getRows().size()
                + " rows. Memory used: "
                + Runtime.getRuntime().totalMemory()
                + " max="
                + Runtime.getRuntime().maxMemory());
      }

      // Compactify if needed
      if (conf.isCompact()) {
        rs = compactify(rs, meta);
      }
      proc.process(rs, meta);
      ++processedObjects;
      if (pm != null) {
        pm.reportProgress(1);
      }
    }
    expect(p, JsonToken.END_OBJECT);
    return processedObjects;
  }

  private void spliceInRelative(
      final String resourceId,
      final RowMetadata meta,
      final Rowset rs,
      final long begin,
      final long end)
      throws HttpException, IOException, ExporterException {
    final Map<RowMetadata.RelationshipSpec, RowMetadata> pMetaList = meta.forRelated();
    final RowSplicer splicer = new RowSplicer(rs, meta, rowsetCache);
    for (final Map.Entry<RowMetadata.RelationshipSpec, RowMetadata> e : pMetaList.entrySet()) {
      final RowMetadata pMeta = e.getValue();
      final RowMetadata.RelationshipSpec relSpec = e.getKey();
      final long now = System.currentTimeMillis();
      final List<NamedResource> relatives =
          dataProvider.getRelativesOf(
              relSpec.getType(),
              resourceId,
              pMeta.getAdapterKind(),
              pMeta.getResourceKind(),
              relSpec.getSearchDepth());
      if (verbose) {
        log.debug(
            "Looked up "
                + relSpec.getType()
                + " of type "
                + relSpec.getType()
                + " "
                + meta.getResourceKind()
                + "("
                + resourceId
                + "). Found "
                + relatives.size());
      }
      if (relatives.isEmpty()) {
        return;
      }
      final StatsProcessor relProcessor =
          new StatsProcessor(conf, pMeta, dataProvider, rowsetCache, new NullProgress(), verbose);

      // If we have a single relative, it's more efficient to try to use the rowset cache. If we're
      // dealing with multiple relatives, we load their rowsets in bulk.
      if (relatives.size() == 1) {
        final NamedResource relative = relatives.get(0);
        final Rowset cached;
        final String cacheKey =
            relative.getIdentifier()
                + relSpec.getType().name()
                + relSpec.getSearchDepth()
                + begin
                + end;
        splicer.setCacheKey(cacheKey);

        // We don't want more than one thread at a time to load a specific rowset since it
        // would
        // result in lots of redundant API calls. So we lock on this cache key specifically
        synchronized (IndexedLocks.instance.getLock(cacheKey)) {
          synchronized (rowsetCache) {
            cached = rowsetCache.get(cacheKey);
          }
          // Try cache first! Chances are we've seen this parent many times.
          if (cached != null) {
            if (verbose) {
              log.debug(
                  "Rowset cache hit for relative "
                      + cacheKey
                      + " "
                      + relative.getResourceKey().get("name"));
            }
            splicer.spliceRows(rs, cached);
          } else {
            // Not in cache. Fetch it the hard (and slow) way!
            if (verbose) {
              log.debug(
                  "Rowset cache miss for relative "
                      + cacheKey
                      + " "
                      + relative.getResourceKey().get("name"));
            }
            try (final InputStream pIs =
                dataProvider.fetchMetricStream(
                    Collections.singletonList(relative), pMeta, begin, end)) {
              relProcessor.process(pIs, splicer, begin, end);
            }
          }
        }

      } else {
        // Multiple relatives. Faster to do them in bulk while ignoring the cache
        try (final InputStream pIs = dataProvider.fetchMetricStream(relatives, pMeta, begin, end)) {
          splicer.setCacheKey(null);
          relProcessor.process(pIs, splicer, begin, end);
        }
      }
      splicer.finish(rs);
      if (verbose) {
        log.debug("Parent processing took " + (System.currentTimeMillis() - now));
      }
    }
  }

  private Rowset compactify(final Rowset rs, final RowMetadata meta) throws ExporterException {
    // No need to process empty rowsets
    if (rs.getRows().size() == 0) {
      return rs;
    }

    // Calculate range according to compactification algorithm.
    final long startTime = System.currentTimeMillis();
    final TreeMap<Long, Row> rows = rs.getRows();
    final long start;
    final long end;
    final long ts;
    final String alg = conf.getCompactifyAlg();
    if (alg == null || alg.equalsIgnoreCase("LATEST")) { // "LATEST" is the default
      end = rows.lastKey();
      start = end - conf.getRollupMinutes() * 60000;
      ts = end;
    } else if (alg.equalsIgnoreCase("MEDIAN")) {
      long median = 0;
      final long half = rows.size() / 2;
      int i = 0;
      for (final long t : rows.keySet()) {
        median = t;
        if (i++ > half) {
          break;
        }
      }
      start = median - conf.getRollupMinutes() * 30000;
      end = median + conf.getRollupMinutes() * 30000;
      ts = median;
    } else if (alg.equalsIgnoreCase("LOCAL")) {
      end = System.currentTimeMillis();
      start = end - conf.getRollupMinutes() * 60000;
      ts = end;
    } else {
      throw new ExporterException("Unknown compactification algorithm: " + alg);
    }

    // Compactify everything that fits within the timerange into a single row
    final Row target = meta.newRow(ts);
    for (final Row r : rows.values()) {
      if (r.getTimestamp() <= end && r.getTimestamp() >= start) {
        target.merge(r);
      }
    }
    final TreeMap<Long, Row> result = new TreeMap<>();
    result.put(ts, target);
    if (verbose) {
      log.debug(
          "Compactifying "
              + rs.getRows().size()
              + " rows took "
              + (System.currentTimeMillis() - startTime)
              + " ms");
    }
    return new Rowset(rs.getResourceId(), result);
  }

  private void expect(final JsonParser p, final JsonToken token)
      throws ExporterException, IOException {
    final JsonToken t = p.nextToken();
    if (t != token) {
      throw new ExporterException("Expected token " + token.asString() + ", got " + t.asString());
    }
  }

  private void expectCurrent(
      final JsonParser p, @SuppressWarnings("SameParameterValue") final JsonToken token)
      throws ExporterException {
    final JsonToken t = p.currentToken();
    if (t != token) {
      throw new ExporterException("Expected token " + token.asString() + ", got " + t.asString());
    }
  }

  private void expect(final JsonParser p, final String fieldname)
      throws ExporterException, IOException {
    if (!expectMaybe(p, fieldname)) {
      throw new ExporterException(
          "Expected field name " + fieldname + ", got " + p.getCurrentName());
    }
  }

  private boolean expectMaybe(final JsonParser p, final String fieldname) throws IOException {
    p.nextToken();
    return fieldname == null || fieldname.equals(p.getCurrentName());
  }

  private boolean expectCurrentMaybe(final JsonParser p, final String fieldname)
      throws IOException {
    return fieldname.equals(p.getCurrentName());
  }

  private boolean skipMemberMaybe(final JsonParser p, final String fieldName)
      throws ExporterException, IOException {
    if (fieldName != null && !expectCurrentMaybe(p, fieldName)) {
      return false;
    }
    expect(p, fieldName); // Advance past the field name
    final JsonToken t = p.currentToken();
    if (t == JsonToken.START_ARRAY) {
      skipComplex(p, 0, 1);
    } else if (t == JsonToken.START_OBJECT) {
      skipComplex(p, 1, 0);
    }
    return true;
  }

  private void skipMember(
      final JsonParser p, @SuppressWarnings("SameParameterValue") final String fieldName)
      throws ExporterException, IOException {
    if (!skipMemberMaybe(p, fieldName)) {
      throw new ExporterException(
          "Expected field name " + fieldName + ", got " + p.getCurrentName());
    }
  }

  private void skipComplex(final JsonParser p, int structLevel, int arrayLevel) throws IOException {
    while (structLevel > 0 || arrayLevel > 0) {
      final JsonToken t = p.nextToken();
      if (t == JsonToken.START_ARRAY) {
        ++arrayLevel;
      } else if (t == JsonToken.END_ARRAY) {
        --arrayLevel;
      } else if (t == JsonToken.START_OBJECT) {
        ++structLevel;
      } else if (t == JsonToken.END_OBJECT) {
        --structLevel;
      }
    }
  }
}
