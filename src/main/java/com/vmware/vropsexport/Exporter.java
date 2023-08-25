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
import com.vmware.vropsexport.models.*;
import com.vmware.vropsexport.processors.*;
import com.vmware.vropsexport.utils.Chunker;
import com.vmware.vropsexport.utils.IndexedLocks;
import com.vmware.vropsexport.utils.LRUCache;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.NoHttpResponseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

@SuppressWarnings("SameParameterValue")
public class Exporter implements DataProvider {
  private final Metadata metadata;

  private static interface ShutdownTask {
    public void run() throws Exception;
  }

  private static class Progress implements ProgressMonitor {
    private final int totalRows;

    private int rowsProcessed = 0;

    public Progress(final int totalRows) {
      this.totalRows = totalRows;
    }

    @Override
    public synchronized void reportProgress(final int n) {
      rowsProcessed += n;
      final int pct = (100 * rowsProcessed) / totalRows;
      System.err.print("" + pct + "% done\r");
    }
  }

  private static final Logger log = LogManager.getLogger(Exporter.class);

  private final LRUCache<String, String> nameCache = new LRUCache<>(100000);

  private final LRUCache<String, Map<String, String>> propCache = new LRUCache<>(1000);

  private final LRUCache<FullyQualifiedId, List<NamedResource>> relativesCache =
      new LRUCache<>(10000);

  private final Client client;

  private final Config conf;

  private final LRUCache<String, Rowset> rowsetCache = new LRUCache<>(2000);

  private static final int MAX_RESPONSE_ROWS =
      100000; // TODO: This is a wild guess. It seems vR Ops barfs on responses that are too long.

  private final boolean verbose;

  private final boolean useTempFile;

  private final ThreadPoolExecutor executor;

  private RowsetProcessorFacotry rspFactory;

  private final int maxRows;

  private final int maxResourceFetch;

  private static final Map<String, RowsetProcessorFacotry> rspFactories = new HashMap<>();

  static {
    rspFactories.put("sql", new SQLDumper.Factory());
    rspFactories.put("csv", new CSVPrinter.Factory());
    rspFactories.put("wavefront", new WavefrontPusher.Factory());
    rspFactories.put("json", new JsonPrinter.Factory());
    rspFactories.put("elasticsearch", new ElasticSearchIndexer.Factory());
  }

  public static boolean isProducingOutput(final Config conf) {
    final RowsetProcessorFacotry rsp = rspFactories.get(conf.getOutputFormat());
    return rsp != null && rsp.isProducingOutput();
  }

  public Exporter(
      final Client client,
      final int threads,
      final Config conf,
      final boolean verbose,
      final boolean useTempFile,
      final int maxRows,
      final int maxResourceFetch)
      throws ExporterException {
    if (conf != null) {
      rspFactory = rspFactories.get(conf.getOutputFormat());
      if (rspFactory == null) {
        throw new ExporterException("Unknown output format: " + conf.getOutputFormat());
      }
    }

    this.verbose = verbose;
    this.useTempFile = useTempFile;
    this.conf = conf;
    this.maxRows = maxRows;
    this.maxResourceFetch = maxResourceFetch;
    this.client = client;
    metadata = new Metadata(client);

    executor =
        new ThreadPoolExecutor(
            threads,
            threads,
            5,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(20),
            new ThreadPoolExecutor.CallerRunsPolicy());
  }

  public void exportTo(
      final OutputStream out,
      final long begin,
      final long end,
      final String parentSpec,
      final boolean quiet)
      throws IOException, HttpException, ExporterException {
    final TimeZone tz = TimeZone.getDefault();
    final Stack<ShutdownTask> postShutdownActions = new Stack<>();
    postShutdownActions.push(() -> out.flush());
    try {
      // Switch default time zone to the user specified one while we're running the export.
      TimeZone.setDefault(TimeZone.getTimeZone(conf.getTimezone()));
      postShutdownActions.push(() -> TimeZone.setDefault(tz));
      Progress progress = null;

      final ResourceRequest query = conf.getQuery();

      final RowMetadata meta =
          conf.isAllMetrics()
              ? new RowMetadata(
                  conf,
                  metadata.getStatKeysForResourceKind(conf.getAdapterKind(), conf.getResourceKind())
                      .stream()
                      .map(ResourceAttributeResponse.ResourceAttribute::getKey)
                      .collect(Collectors.toList()))
              : new RowMetadata(conf);
      final RowsetProcessor rsp = rspFactory.makeFromConfig(out, conf, this);
      postShutdownActions.push(() -> rsp.close());
      rsp.preamble(meta, conf);
      String parentId = null;
      if (parentSpec != null) {

        // Lookup parent
        final Matcher m = Patterns.parentSpecPattern.matcher(parentSpec);
        if (!m.matches()) {
          throw new ExporterException(
              "Not a valid parent spec: "
                  + parentSpec
                  + ". should be on the form ResourceKind:resourceName");
        }
        // TODO: No way of specifying adapter type here. Should there be?
        final ResourceRequest parentQuery = new ResourceRequest();
        parentQuery.setAdapterKind(Collections.singletonList(m.group(1)));
        parentQuery.setResourceKind(Collections.singletonList(m.group(2)));
        final List<NamedResource> pResources = fetchResources(query, 0).getResourceList();
        if (pResources.size() == 0) {
          throw new ExporterException("Parent not found");
        }
        if (pResources.size() > 1) {
          throw new ExporterException("Parent spec is not unique");
        }
        parentId = pResources.get(0).getIdentifier();
      }

      int page = 0;
      for (; ; ) {
        final PageOfResources resPage;

        // Fetch resources
        if (parentId != null) {
          final String url = "/suite-api/api/resources/" + parentId + "/relationships";
          resPage =
              client.getJson(
                  url, PageOfResources.class, "relationshipType=CHILD", "page=" + page++);
        } else {
          resPage = fetchResources(query, page++);
        }

        final List<NamedResource> resources = resPage.getResourceList();
        // If we got an empty set back, we ran out of pages.
        if (resources.size() == 0) {
          break;
        }

        // Initialize progress reporting
        if (!quiet && progress == null) {
          progress = new Progress(resPage.getPageInfo().getTotalCount());
          progress.reportProgress(0);
        }
        int chunkSize = Math.min(MAX_RESPONSE_ROWS, maxRows);
        if (verbose) {
          log.debug("Raw chunk size is " + chunkSize + " resources");
        }

        // We don't want to make the chunks so big that not all threads will have work to do.
        // Make sure that doesn't happen.
        chunkSize = Math.min(chunkSize, 1 + (resources.size() / executor.getMaximumPoolSize()));
        if (verbose) {
          log.debug("Adjusted chunk size is " + chunkSize + " resources");
        }
        final Progress finalProgress = progress;
        // Child relationships may return objects of the wrong type, so we have
        // to check the type here.
        final Stream<NamedResource> filteredResources =
            resources.stream()
                .filter((r) -> r.isSameType(conf.getAdapterKind(), conf.getResourceKind()));
        Chunker.chunkify(
            filteredResources,
            chunkSize,
            (chunk) -> {
              startChunkJob(chunk, rsp, meta, begin, end, finalProgress);
            });

        final ArrayList<NamedResource> chunk = new ArrayList<>(chunkSize);
      }
    } finally {
      executor.shutdown();
      try {
        // We have no idea how long it's going to take, so pick a ridiculously long timeout.
        if (verbose) {
          log.debug("Waiting for all threads to finish");
        }
        executor.awaitTermination(1, TimeUnit.DAYS);
        if (verbose) {
          log.debug("All threads have finished!");
        }
      } catch (final InterruptedException e) {
        // Shouldn't happen...
        e.printStackTrace();
        return;
      }
      while (!postShutdownActions.isEmpty()) {
        try {
          postShutdownActions.pop().run();
        } catch (final Exception e) {
          e.printStackTrace();
        }
      }
    }
    if (!quiet) {
      System.err.println("100% done");
    }
  }

  private void startChunkJob(
      final List<NamedResource> chunk,
      final RowsetProcessor rsp,
      final RowMetadata meta,
      final long begin,
      final long end,
      final ProgressMonitor progress) {
    executor.execute(
        () -> {
          try {
            preloadCache(chunk);
            handleResources(chunk, rsp, meta, begin, end, progress);
          } catch (final Exception e) {
            log.error("Error while processing resource", e);
          }
        });
  }

  private void preloadCache(final List<NamedResource> resources) {
    synchronized (nameCache) {
      for (final NamedResource res : resources) {
        nameCache.put(res.getIdentifier(), (String) res.getResourceKey().get("name"));
      }
    }
  }

  private PageOfResources fetchResources(final ResourceRequest query, final int page)
      throws IOException, HttpException {
    final String url =
        "/suite-api/api/resources/query?page=" + page + "&pageSize=" + maxResourceFetch;
    final PageOfResources response = client.postJsonReturnJson(url, query, PageOfResources.class);
    if (verbose) {
      log.debug("Resources found: " + response.getPageInfo().getTotalCount());
    }
    return response;
  }

  @Override
  public String getResourceName(final String resourceId) throws IOException, HttpException {
    synchronized (nameCache) {
      final String name = nameCache.get(resourceId);
      if (name != null) {
        return name;
      }
    }
    final long start = System.currentTimeMillis();
    final String url = "/suite-api/api/resources/" + resourceId;
    final NamedResource res = client.getJson(url, NamedResource.class);
    final String name = (String) res.getResourceKey().get("name");
    synchronized (nameCache) {
      nameCache.put(resourceId, name);
    }
    if (verbose) {
      log.debug("Name cache miss. Lookup took " + (System.currentTimeMillis() - start));
    }
    return name;
  }

  @Override
  public InputStream fetchMetricStream(
      final List<NamedResource> resList, final RowMetadata meta, final long begin, final long end)
      throws IOException, HttpException {
    return conf.getRollupType().equals("LATEST")
        ? fetchLatestMetrics(resList, meta)
        : queryMetrics(resList, meta, begin, end);
  }

  private InputStream fetchLatestMetrics(final List<NamedResource> resList, final RowMetadata meta)
      throws IOException, HttpException {
    final MetricsRequest q =
        new MetricsRequest(
            resList.stream().map(NamedResource::getIdentifier).collect(Collectors.toList()),
            true,
            "LATEST",
            "MINUTES",
            1,
            1,
            null,
            null,
            meta.getMetrics().stream().map(Field::getLocalName).collect(Collectors.toList()));
    return client.postJsonReturnStream("/suite-api/api/resources/stats/latest/query", q);
  }

  private InputStream queryMetrics(
      final List<NamedResource> resList, final RowMetadata meta, final long begin, final long end)
      throws IOException, HttpException {
    final MetricsRequest q =
        new MetricsRequest(
            resList.stream().map(NamedResource::getIdentifier).collect(Collectors.toList()),
            false,
            conf.getRollupType(),
            "MINUTES",
            (int) conf.getRollupMinutes(),
            null,
            begin,
            end,
            meta.getMetrics().stream().map(Field::getLocalName).collect(Collectors.toList()));
    // log.debug("Metric query: " + new ObjectMapper().writeValueAsString(q));
    return client.postJsonReturnStream("/suite-api/api/resources/stats/query", q);
  }

  @Override
  public List<String> getStatKeysForResource(final String resourceId)
      throws IOException, HttpException {
    return metadata.getStatKeysForResource(resourceId);
  }

  @Override
  public Map<String, String> fetchProps(final String id) throws IOException, HttpException {
    synchronized (propCache) {
      final Map<String, String> result = propCache.get(id);
      if (result != null) {
        return result;
      }
    }

    if (verbose) {
      log.debug("Prop cache miss for id: " + id);
    }
    final String uri = "/suite-api/api/resources/" + id + "/properties";
    final PropertiesResponse props = client.getJson(uri, PropertiesResponse.class);
    final Map<String, String> response =
        Arrays.stream(props.getProperty())
            .collect(
                Collectors.toMap(
                    PropertiesResponse.Property::getName, PropertiesResponse.Property::getValue));
    synchronized (propCache) {
      propCache.put(id, response);
    }
    return response;
  }

  private void getRelativesOf(
      final Field.RelationshipType type,
      final FullyQualifiedId key,
      final int maxDepth,
      final List<NamedResource> list)
      throws HttpException, IOException {
    // Prevent multiple threads from looking for the same resource. This can cause lots of redundant
    // API calls.
    synchronized (IndexedLocks.instance.getLock(key)) {
      synchronized (relativesCache) {
        final List<NamedResource> p = relativesCache.get(key);
        if (p != null) {
          list.addAll(p);
          return;
        }
      }
      if (verbose) {
        log.debug("Relatives cache miss for: " + key);
      }
      final PageOfResources page =
          client.getJson(
              "/suite-api/api/resources/" + key.getId() + "/relationships",
              PageOfResources.class,
              "relationshipType=" + type.name().toUpperCase());

      // Correct resource type? Add it to the list!
      final int size = list.size();
      for (final NamedResource res : page.getResourceList()) {
        if ((key.getAdapterKind() == null
                || res.getResourceKey().get("adapterKindKey").equals(key.getAdapterKind()))
            && res.getResourceKey().get("resourceKindKey").equals(key.getResourceKind())) {
          list.add(res);
          continue;
        }
        // Not the right resource. Recursively search until we've hit the max depth
        if (maxDepth > 0) {
          getRelativesOf(
              type,
              new FullyQualifiedId(
                  key.getAdapterKind(), key.getResourceKind(), res.getIdentifier()),
              maxDepth - 1,
              list);
        }
      }
      synchronized (relativesCache) {
        relativesCache.put(key, new ArrayList<>(list.subList(size, list.size())));
      }
    }
  }

  @Override
  public List<NamedResource> getRelativesOf(
      final Field.RelationshipType type,
      final String id,
      final String parentAdapterKind,
      final String parentResourceKind,
      final int maxDepth)
      throws IOException, HttpException {
    final FullyQualifiedId key = new FullyQualifiedId(parentAdapterKind, parentResourceKind, id);
    final List<NamedResource> list = new ArrayList<>();
    getRelativesOf(type, key, maxDepth - 1, list);

    // There might be multiple paths to some objects, so let's make sure we only return unique
    // objects.
    return list.stream().distinct().collect(Collectors.toList());
  }

  public void printResourceMetadata(final String adapterAndResourceKind, final PrintStream out)
      throws IOException, HttpException {
    String resourceKind = adapterAndResourceKind;
    String adapterKind = "VMWARE";
    final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(adapterAndResourceKind);
    if (m.matches()) {
      adapterKind = m.group(1);
      resourceKind = m.group(2);
    }
    final ResourceAttributeResponse response =
        client.getJson(
            "/suite-api/api/adapterkinds/"
                + Exporter.urlencode(adapterKind)
                + "/resourcekinds/"
                + Exporter.urlencode(resourceKind)
                + "/statkeys",
            ResourceAttributeResponse.class);
    for (final ResourceAttributeResponse.ResourceAttribute key : response.getResourceAttributes()) {
      out.println("Key  : " + key.getKey());
      out.println("Name : " + key.getName());
      out.println();
    }
  }

  public void generateExportDefinition(final String adapterAndResourceKind, final PrintStream out)
      throws IOException, HttpException {
    String resourceKind = adapterAndResourceKind;
    String adapterKind = "VMWARE";
    final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(adapterAndResourceKind);
    if (m.matches()) {
      adapterKind = m.group(1);
      resourceKind = m.group(2);
    }

    final List<ResourceAttributeResponse.ResourceAttribute> metrics =
        metadata.getStatKeysForResourceKind(adapterKind, resourceKind);
    final List<Field> fields =
        metrics.stream()
            .map(
                s ->
                    new Field(
                        s.getKey(),
                        s.getKey(),
                        Field.Kind.metric,
                        null,
                        null,
                        Field.RelationshipType.self))
            .collect(Collectors.toList());
    final Config config = new Config();
    config.setFields(fields);
    final DumperOptions dumperOptions = new DumperOptions();
    dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    dumperOptions.setPrettyFlow(true);

    final Representer representer =
        new Representer(new DumperOptions()) {
          @Override
          protected NodeTuple representJavaBeanProperty(
              final Object javaBean,
              final Property property,
              final Object propertyValue,
              final Tag customTag) {
            // if value of property is null, ignore it.
            if (propertyValue == null) {
              return null;
            } else {
              return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
            }
          }
        };
    final Yaml y = new Yaml(representer, dumperOptions);
    y.dump(config, new OutputStreamWriter(out, StandardCharsets.UTF_8));
  }

  public void printResourceKinds(String adapterKind, final PrintStream out)
      throws IOException, HttpException {
    if (adapterKind == null) {
      adapterKind = "VMWARE";
    }
    for (final ResourceKind rk : metadata.getResourceKinds(adapterKind)) {
      out.println("Key  : " + rk.getKey());
      out.println("Name : " + rk.getName());
      out.println();
    }
  }

  public void printAdapterKinds(final PrintStream out) throws IOException, HttpException {
    for (final AdapterKind ak : metadata.getAdapterKinds()) {
      out.println("Key  : " + ak.getKey());
      out.println("Name : " + ak.getName());
      out.println();
    }
  }

  private void handleResources(
      final List<NamedResource> resList,
      final RowsetProcessor rsp,
      final RowMetadata meta,
      final long begin,
      final long end,
      final ProgressMonitor progress)
      throws IOException, HttpException, ExporterException {
    InputStream content;
    try {
      final long start = System.currentTimeMillis();
      content = fetchMetricStream(resList, meta, begin, end);
      if (verbose) {
        log.debug("Metric request call took " + (System.currentTimeMillis() - start) + " ms");
      }
    } catch (final NoHttpResponseException e) {

      // This seems to happen when we're giving the server too much work to do in one call.
      // Try again, but split the chunk into two and run them separately.
      final int sz = resList.size();
      if (sz <= 1) {
        // Already down to one item? We're out of luck!
        throw new ExporterException(e);
      }
      // Split lists and try them separately
      final int half = sz / 2;
      log.warn("Server closed connection. Trying smaller chunk (current=" + sz + ")");
      final List<NamedResource> left = new ArrayList<>(half);
      final List<NamedResource> right = new ArrayList<>(sz - half);
      int i = 0;
      while (i < half) {
        left.add(resList.get(i++));
      }
      while (i < sz) {
        right.add(resList.get(i++));
      }
      handleResources(left, rsp, meta, begin, end, progress);
      handleResources(right, rsp, meta, begin, end, progress);
      return;
    }
    try {
      if (useTempFile) {
        // Dump to temp file
        File tmpFile;
        final long start = System.currentTimeMillis();
        try {
          tmpFile = File.createTempFile("vrops-export", ".tmp");
          try (final FileOutputStream out = new FileOutputStream(tmpFile)) {
            IOUtils.copy(content, out);
          }

        } finally {
          content.close();
        }
        content = new SelfDeletingFileInputStream(tmpFile);
        if (verbose) {
          log.debug("Dumping to temp file took " + (System.currentTimeMillis() - start) + " ms");
        }
      }
      final long start = System.currentTimeMillis();
      final StatsProcessor sp =
          new StatsProcessor(conf, meta, this, rowsetCache, progress, verbose);
      final int processed = sp.process(content, rsp, begin, end);

      // Some resources may not have returned metrics and would not have been counted. Update the
      // progress counter
      // to make sure we're still in synch.
      if (progress != null) {
        progress.reportProgress(resList.size() - processed);
      }
      if (verbose) {
        log.debug("Found data for " + processed + " out of " + resList.size() + " resources.");
        log.debug("Result processing took " + (System.currentTimeMillis() - start) + " ms");
      }
    } finally {
      content.close();
    }
  }

  public static String urlencode(final String s) throws UnsupportedEncodingException {
    return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
  }
}
