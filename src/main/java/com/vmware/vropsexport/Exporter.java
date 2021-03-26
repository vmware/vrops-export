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

import com.vmware.vropsexport.processors.CSVPrinter;
import com.vmware.vropsexport.processors.JsonPrinter;
import com.vmware.vropsexport.processors.SQLDumper;
import com.vmware.vropsexport.processors.WavefrontPusher;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.NoHttpResponseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

@SuppressWarnings("SameParameterValue")
public class Exporter implements DataProvider {
    private class Progress implements ProgressMonitor {
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

    private static final Log log = LogFactory.getLog(Exporter.class);

    private final LRUCache<String, String> nameCache = new LRUCache<>(100000);

    private final LRUCache<String, Map<String, String>> propCache = new LRUCache<>(1000);

    private final LRUCache<String, JSONObject> parentCache = new LRUCache<>(1000);

    private final Client client;

    private final Config conf;

    private final LRUCache<String, Rowset> rowsetCache = new LRUCache<>(2000);

    private static final int MAX_RESPONSE_ROWS = 100000; // TODO: This is a wild guess. It seems vR Ops barfs on responses that are too long.

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
    }

    public static boolean isProducingOutput(final Config conf) {
        final RowsetProcessorFacotry rsp = rspFactories.get(conf.getOutputFormat());
        return rsp != null && rsp.isProducingOutput();
    }

    public Exporter(final String urlBase, final String username, final String password, final int threads, final Config conf, final boolean verbose, final boolean useTempFile, final int maxRows,
                    final int maxResourceFetch, final KeyStore extendedTrust)
            throws IOException, HttpException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException, ExporterException {
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
        client = new Client(urlBase, username, password, extendedTrust);

        executor = new ThreadPoolExecutor(threads, threads, 5, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(20), new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public void exportTo(final OutputStream out, final long begin, final long end, final String namePattern, final String parentSpec, final boolean quiet) throws IOException, HttpException, ExporterException {
        Progress progress = null;

        final RowMetadata meta = conf.isAllMetrics()
                ? new RowMetadata(conf, getStatKeysForResourceKind(conf.getAdapterKind(), conf.getResourceKind()))
                : new RowMetadata(conf);
        final RowsetProcessor rsp = rspFactory.makeFromConfig(out, conf, this);
        rsp.preamble(meta, conf);
        JSONArray resources;
        String parentId = null;
        if (parentSpec != null) {
            // Lookup parent
            //
            final Matcher m = Patterns.parentSpecPattern.matcher(parentSpec);
            if (!m.matches()) {
                throw new ExporterException("Not a valid parent spec: " + parentSpec + ". should be on the form ResourceKind:resourceName");
            }
            // TODO: No way of specifying adapter type here. Should there be?
            //
            final JSONArray pResources = fetchResources(m.group(1), null, m.group(2), 0).getJSONArray("resourceList");
            if (pResources.length() == 0) {
                throw new ExporterException("Parent not found");
            }
            if (pResources.length() > 1) {
                throw new ExporterException("Parent spec is not unique");
            }
            parentId = pResources.getJSONObject(0).getString("identifier");
        }

        int page = 0;
        for (; ; ) {
            final JSONObject resObj;

            // Fetch resources
            //
            if (parentId != null) {
                final String url = "/suite-api/api/resources/" + parentId + "/relationships";
                resObj = client.getJson(url, "relationshipType=CHILD", "page=" + page++);
            } else {
                resObj = fetchResources(conf.getResourceKind(), conf.getAdapterKind(), namePattern, page++);
            }
            resources = resObj.getJSONArray("resourceList");

            // If we got an empty set back, we ran out of pages.
            //
            if (resources.length() == 0) {
                break;
            }

            // Initialize progress reporting
            //
            if (!quiet && progress == null) {
                progress = new Progress(resObj.getJSONObject("pageInfo").getInt("totalCount"));
                progress.reportProgress(0);
            }
            int chunkSize = Math.min(MAX_RESPONSE_ROWS, maxRows);
            if (verbose) {
                System.err.println("Raw chunk size is " + chunkSize + " resources");
            }

            // We don't want to make the chunks so big that not all threads will have work to do.
            // Make sure that doesn't happen.
            //
            chunkSize = Math.min(chunkSize, 1 + (resources.length() / executor.getMaximumPoolSize()));
            if (verbose) {
                System.err.println("Adjusted chunk size is " + chunkSize + " resources");
            }
            ArrayList<JSONObject> chunk = new ArrayList<>(chunkSize);
            for (int i = 0; i < resources.length(); ++i) {
                final JSONObject res = resources.getJSONObject(i);
                chunk.add(res);
                if (chunk.size() >= chunkSize || i == resources.length() - 1) {

                    // Child relationships may return objects of the wrong type, so we have
                    // to check the type here.
                    //
                    final JSONObject rKey = res.getJSONObject("resourceKey");
                    if (!rKey.getString("resourceKindKey").equals(conf.getResourceKind())) {
                        continue;
                    }
                    if (conf.getAdapterKind() != null && !rKey.getString("adapterKindKey").equals(conf.getAdapterKind())) {
                        continue;
                    }
                    startChunkJob(out, chunk, rsp, meta, begin, end, progress);
                    chunk = new ArrayList<>(chunkSize);
                }
            }
        }
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            // Shouldn't happen...
            //
            e.printStackTrace();
            return;
        }
        out.flush();
        rsp.close();
        if (!quiet) {
            System.err.println("100% done");
        }
    }

    private void startChunkJob(final OutputStream out, final List<JSONObject> chunk, final RowsetProcessor rsp, final RowMetadata meta, final long begin, final long end, final ProgressMonitor progress) {
        executor.execute(() -> {
            try {
                preloadCache(chunk);
                handleResources(chunk, rsp, meta, begin, end, progress);
            } catch (final Exception e) {
                log.error("Error while processing resource", e);
            }
        });
    }

    private void preloadCache(final List<JSONObject> resources) {
        for (final JSONObject res : resources) {
            nameCache.put(res.getString("identifier"), res.getJSONObject("resourceKey").getString("name"));
        }
    }

    private JSONObject fetchResources(final String resourceKind, final String adapterKind, final String name, final int page) throws JSONException, IOException, HttpException {
        final String url = "/suite-api/api/resources";
        final ArrayList<String> qs = new ArrayList<>();
        if (adapterKind != null) {
            qs.add("adapterKind=" + urlencode(adapterKind));
        }
        qs.add("resourceKind=" + urlencode(resourceKind));
        qs.add("pageSize=" + maxResourceFetch);
        qs.add("page=" + page);
        if (name != null) {
            qs.add("name=" + name);
        }
        final JSONObject response = client.getJson(url, qs);
        if (verbose) {
            System.err.println("Resources found: " + response.getJSONObject("pageInfo").getInt("totalCount"));
        }
        return response;
    }

    @Override
    public String getResourceName(final String resourceId) throws JSONException, IOException, HttpException {
        synchronized (nameCache) {
            final String name = nameCache.get(resourceId);
            if (name != null) {
                return name;
            }
        }
        final long start = System.currentTimeMillis();
        final String url = "/suite-api/api/resources/" + resourceId;
        final JSONObject res = client.getJson(url);
        final String name = res.getJSONObject("resourceKey").getString("name");
        synchronized (nameCache) {
            nameCache.put(resourceId, name);
        }
        if (verbose) {
            System.err.println("Name cache miss. Lookup took " + (System.currentTimeMillis() - start));
        }
        return name;
    }

    @Override
    public InputStream fetchMetricStream(final List<JSONObject> resList, final RowMetadata meta, final long begin, final long end) throws IOException, HttpException {
        return conf.getRollupType().equals("LATEST")
                ? fetchLatestMetrics(resList, meta)
                : queryMetrics(resList, meta, begin, end);
    }

    private InputStream fetchLatestMetrics(final List<JSONObject> resList, final RowMetadata meta) throws IOException, HttpException {
        final JSONObject q = new JSONObject();
        final JSONArray ids = new JSONArray();
        for (final JSONObject res : resList) {
            ids.put(res.getString("identifier"));
        }
        q.put("resourceId", ids);
        q.put("currentOnly", "true");
        q.put("rollUpType", "LATEST");
        q.put("maxSamples", 1);
        final JSONArray stats = new JSONArray();
        for (final String f : meta.getMetricMap().keySet()) {
            stats.put(f);
        }
        q.put("statKey", stats);
        return client.postJsonReturnStream("/suite-api/api/resources/stats/latest/query", q);
    }

    private InputStream queryMetrics(final List<JSONObject> resList, final RowMetadata meta, final long begin, final long end) throws IOException, HttpException {
        final JSONObject q = new JSONObject();
        final JSONArray ids = new JSONArray();
        for (final JSONObject res : resList) {
            ids.put(res.getString("identifier"));
        }
        q.put("resourceId", ids);
        q.put("rollUpType", conf.getRollupType());
        q.put("intervalType", "MINUTES");
        q.put("intervalQuantifier", conf.getRollupMinutes());
        q.put("begin", begin);
        q.put("end", end);
        final JSONArray stats = new JSONArray();
        for (final String f : meta.getMetricMap().keySet()) {
            stats.put(f);
        }
        q.put("statKey", stats);
        return client.postJsonReturnStream("/suite-api/api/resources/stats/query", q);
    }

    @Override
    public Map<String, String> fetchProps(final String id) throws IOException, HttpException {
        synchronized (propCache) {
            Map<String, String> result = propCache.get(id);
            if (result != null) {
                return result;
            }

            if (verbose) {
                System.err.println("Prop cache miss for id: " + id);
            }
            final String uri = "/suite-api/api/resources/" + id + "/properties";
            final JSONObject json = client.getJson(uri);
            final JSONArray props = json.getJSONArray("property");
            result = new HashMap<>(props.length());
            for (int i = 0; i < props.length(); ++i) {
                final JSONObject p = props.getJSONObject(i);
                result.put(p.getString("name"), p.getString("value"));
            }
            propCache.put(id, result);
            return result;
        }
    }

    @Override
    public JSONObject getParentOf(final String id, final String parentType) throws JSONException, IOException, HttpException {
        synchronized (parentCache) {
            final JSONObject p = parentCache.get(id + parentType);
            if (p != null) {
                return p;
            }
        }
        if (verbose) {
            System.err.println("Parent cache miss for id: " + id);
        }
        final JSONObject json = client.getJson("/suite-api/api/resources/" + id + "/relationships", "relationshipType=PARENT");
        final JSONArray rl = json.getJSONArray("resourceList");
        for (int i = 0; i < rl.length(); ++i) {
            final JSONObject r = rl.getJSONObject(i);

            // If there's more than one we only return the first one.
            //
            if (r.getJSONObject("resourceKey").getString("resourceKindKey").equals(parentType)) {
                synchronized (parentCache) {
                    parentCache.put(id + parentType, r);
                    return r;
                }
            }
        }
        return null;
    }

    public void printResourceMetadata(final String adapterAndResourceKind, final PrintStream out) throws IOException, HttpException {
        String resourceKind = adapterAndResourceKind;
        String adapterKind = "VMWARE";
        final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(adapterAndResourceKind);
        if (m.matches()) {
            adapterKind = m.group(1);
            resourceKind = m.group(2);
        }
        final JSONObject response = client.getJson("/suite-api/api/adapterkinds/" +
                urlencode(adapterKind) + "/resourcekinds/" + urlencode(resourceKind) + "/statkeys");
        final JSONArray stats = response.getJSONArray("resourceTypeAttributes");
        for (int i = 0; i < stats.length(); ++i) {
            final JSONObject stat = stats.getJSONObject(i);
            out.println("Key  : " + stat.getString("key"));
            out.println("Name : " + stat.getString("name"));
            out.println();
        }
    }

    public List<String> getStatKeysForResourceKind(final String adapterKind, final String resourceKind) throws IOException, HttpException {
        final JSONObject response = client.getJson("/suite-api/api/adapterkinds/" +
                urlencode(adapterKind) + "/resourcekinds/" + urlencode(resourceKind) + "/statkeys");
        final JSONArray stats = response.getJSONArray("resourceTypeAttributes");
        final List<String> metrics = new ArrayList<>(stats.length());
        for (int i = 0; i < stats.length(); ++i) {
            final JSONObject stat = stats.getJSONObject(i);
            metrics.add(stat.getString("key"));
        }
        return metrics;
    }


    @Override
    public List<String> getStatKeysForResource(final String resourceId) throws IOException, HttpException {
        final JSONObject response = client.getJson("/suite-api/api/resources/" + resourceId + "/statkeys");
        final JSONArray stats = response.getJSONArray("stat-key");
        final List<String> metrics = new ArrayList<>(stats.length());
        for (int i = 0; i < stats.length(); ++i) {
            final JSONObject stat = stats.getJSONObject(i);
            metrics.add(stat.getString("key"));
        }
        return metrics;
    }

    public void generateExportDefinition(final String adapterAndResourceKind, final PrintStream out) throws IOException, HttpException {
        String resourceKind = adapterAndResourceKind;
        String adapterKind = "VMWARE";
        final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(adapterAndResourceKind);
        if (m.matches()) {
            adapterKind = m.group(1);
            resourceKind = m.group(2);
        }

        final List<String> metrics = getStatKeysForResourceKind(adapterKind, resourceKind);
        final List<Config.Field> fields = metrics.stream().map(s -> new Config.Field(s, s, true)).collect(Collectors.toList());
        final Config config = new Config();
        final Config.Field[] fieldArr = new Config.Field[fields.size()];
        fields.toArray(fieldArr);
        config.setFields(fieldArr);
        final DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);

        final Representer representer = new Representer() {
            @Override
            protected NodeTuple representJavaBeanProperty(final Object javaBean, final Property property, final Object propertyValue, final Tag customTag) {
                // if value of property is null, ignore it.
                if (propertyValue == null) {
                    return null;
                } else {
                    return super.representJavaBeanProperty(javaBean, property, propertyValue, customTag);
                }
            }
        };
        final Yaml y = new Yaml(representer, dumperOptions);
        y.dump(config, new OutputStreamWriter(out));
    }

    public void printResourceKinds(String adapterKind, final PrintStream out) throws IOException, HttpException {
        if (adapterKind == null) {
            adapterKind = "VMWARE";
        }
        final JSONObject response = client.getJson("/suite-api/api/adapterkinds/" + urlencode(adapterKind) + "/resourcekinds");
        final JSONArray kinds = response.getJSONArray("resource-kind");
        for (int i = 0; i < kinds.length(); ++i) {
            final JSONObject kind = kinds.getJSONObject(i);
            out.println("Key  : " + kind.getString("key"));
            out.println("Name : " + kind.getString("name"));
            out.println();
        }
    }

    private void handleResources(final List<JSONObject> resList, final RowsetProcessor rsp, final RowMetadata meta, final long begin, final long end, final ProgressMonitor progress) throws IOException, HttpException, ExporterException {
        InputStream content;
        try {
            final long start = System.currentTimeMillis();
            content = fetchMetricStream(resList, meta, begin, end);
            if (verbose) {
                System.err.println("Metric request call took " + (System.currentTimeMillis() - start) + " ms");
            }
        } catch (final NoHttpResponseException e) {
            // This seems to happen when we're giving the server too much work to do in one call.
            // Try again, but split the chunk into two and run them separately.
            //
            final int sz = resList.size();
            if (sz <= 1) {
                // Already down to one item? We're out of luck!
                //
                throw new ExporterException(e);
            }
            // Split lists and try them separately
            //
            final int half = sz / 2;
            log.warn("Server closed connection. Trying smaller chunk (current=" + sz + ")");
            final List<JSONObject> left = new ArrayList<>(half);
            final List<JSONObject> right = new ArrayList<>(sz - half);
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
                //
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
                    System.err.println("Dumping to temp file took " + (System.currentTimeMillis() - start) + " ms");
                }
            }
            final long start = System.currentTimeMillis();
            final StatsProcessor sp = new StatsProcessor(conf, meta, this, rowsetCache, progress, verbose);
            final int processed = sp.process(content, rsp, begin, end);

            // Some resources may not have returned metrics and would not have been counted. Update the progress counter
            // to make sure we're still in synch.
            //
            if (progress != null) {
                progress.reportProgress(resList.size() - processed);
            }
            if (verbose) {
                System.err.println("Found data for " + processed + " out of " + resList.size() + " resources.");
                System.err.println("Result processing took " + (System.currentTimeMillis() - start) + " ms");
            }
        } finally {
            content.close();
        }
    }

    private static String urlencode(final String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
}
