/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport.processors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import com.vmware.vropsexport.elasticsearch.ElasticSearchConfig;
import com.vmware.vropsexport.json.JsonProducer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;

public class ElasticSearchPoster implements RowsetProcessor {
  public static class Factory implements RowsetProcessorFacotry {

    @Override
    public RowsetProcessor makeFromConfig(
        final OutputStream out, final Config config, final DataProvider dp)
        throws ExporterException {
      return new ElasticSearchPoster(config, dp);
    }

    @Override
    public boolean isProducingOutput() {
      return false;
    }
  }

  private static final Logger log = LogManager.getLogger(ElasticSearchPoster.class);

  private static final long RETRY_DELAY = 1000;

  private static final long MAX_RETRY_DELAY = 64000;

  private final String url;

  private final RestHighLevelClient client;

  private final String index;

  private final String type;

  private final DataProvider dataProvider;

  private final DateFormat dateFormat;

  private final int bulkSize;

  public ElasticSearchPoster(final Config config, final DataProvider dataProvider)
      throws ExporterException {
    final ElasticSearchConfig ec = config.getElasticSearchConfig();
    if (ec == null) {
      throw new ExporterException("The configuration is missing the 'elasticSearchConfig' section");
    }
    if (ec.getUrl() == null) {
      throw new ExporterException("The 'url' setting is missing from the configuration");
    }
    url = ec.getUrl();
    if (ec.getIndex() == null) {
      throw new ExporterException("The 'index' setting is missing from the configuration");
    }
    index = ec.getIndex();
    if (ec.getIndex() == null) {
      throw new ExporterException("The 'indexType' setting is missing from the configuration");
    }
    type = ec.getType();
    final RestClientBuilder builder = RestClient.builder(HttpHost.create(url));
    if (ec.getApiKey() != null) {
      builder.setDefaultHeaders(
          new Header[] {new BasicHeader("Authorization", "ApiKey " + ec.getApiKey())});
    }
    client = new RestHighLevelClient(builder);

    this.dataProvider = dataProvider;
    dateFormat = config.getDateFormatter();
    bulkSize = ec.getBulkSize();
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {}

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
    try {
      BulkRequest br = new BulkRequest();
      for (final Map.Entry<Long, Row> entry : rowset.getRows().entrySet()) {
        if (br.numberOfActions() < bulkSize) {
          final ByteArrayOutputStream out = new ByteArrayOutputStream();
          final JsonFactory jf = new JsonFactory();
          final JsonGenerator generator = jf.createGenerator(out, JsonEncoding.UTF8);
          final JsonProducer producer = new JsonProducer(generator, dataProvider, dateFormat);
          producer.produceSingleElastic(
              entry.getValue(), entry.getKey(), rowset.getResourceId(), meta);
          generator.close();
          out.close();
          br.add(
              new IndexRequest(index)
                  .type(type)
                  .id(UUID.randomUUID().toString())
                  .source(out.toString("UTF-8"), XContentType.JSON));
        } else {
          sumbitBulk(br);
          br = new BulkRequest();
        }
      }
      if (br.numberOfActions() > 0) {
        sumbitBulk(br);
      }
    } catch (final IOException | HttpException | InterruptedException e) {
      throw new ExporterException(e);
    }
  }

  private void sumbitBulk(final BulkRequest br) throws IOException, InterruptedException {
    long delay = RETRY_DELAY;
    for (; ; ) {
      // Add some fuzz to the delay to make threads run out of synch. Otherwise, we'd have
      // all threads bombarding the backend at the same time, most likely causing us
      // to end up overloading it again.
      final long actualDelay = delay + ThreadLocalRandom.current().nextLong(delay / 2);
      try {
        final BulkResponse response = client.bulk(br, RequestOptions.DEFAULT);
        return;
      } catch (final ElasticsearchStatusException e) {
        if (!(e.getCause() instanceof ResponseException)) {
          throw e;
        }
        final StatusLine status = ((ResponseException) e.getCause()).getResponse().getStatusLine();
        if (status.getStatusCode() == 403
            && status.getReasonPhrase().contains("Request throttled")) {
          // We overloaded the cluster. Delay for a while to let it catch up!
          log.warn(
              "Cluster overloaded (requests throttled). Waiting "
                  + actualDelay
                  + "ms to let it catch up");
          Thread.sleep(actualDelay);
        } else if (status.getStatusCode() == 503) {
          log.warn(
              "Cluster overloaded (service unavailable). Waiting "
                  + actualDelay
                  + "ms to let it catch up");
          Thread.sleep(actualDelay);
        } else {
          throw e;
        }
      } catch (final ConnectionClosedException | SocketTimeoutException e) {
        // The cluster is very overloaded and is cutting us off. Delay and retry
        log.warn(
            "Cluster overloaded (socket-level timeout). Waiting "
                + actualDelay
                + "ms to let it catch up");
        Thread.sleep(actualDelay);
      } catch (final ConnectException e) {
        if (e.getMessage().contains("Timeout connecting to")) {
          log.warn(
              "Cluster overloaded (connection timeout). Waiting "
                  + actualDelay
                  + "ms to let it catch up");
          Thread.sleep(actualDelay);
        } else {
          throw e;
        }
      }
      if (delay < MAX_RETRY_DELAY) {
        delay *= 2;
      }
    }
  }

  @Override
  public void close() throws ExporterException {
    try {
      Listener.instance.waitUntilIdle();
      client.close();
    } catch (final IOException | InterruptedException e) {
      throw new ExporterException(e);
    }
  }

  private static class Listener implements ActionListener<IndexResponse> {

    private static final Listener instance = new Listener();

    private final AtomicInteger outstandingRq = new AtomicInteger(0);

    public void notifyRequest() {
      outstandingRq.incrementAndGet();
    }

    public void waitUntilIdle() throws InterruptedException {
      synchronized (this) {
        while (outstandingRq.get() > 0) {
          wait();
        }
      }
    }

    @Override
    public void onResponse(final IndexResponse o) {
      decrementOutstanding();
      System.err.println("Outstanding: " + outstandingRq.get());
    }

    @Override
    public void onFailure(final Exception e) {
      decrementOutstanding();
      System.err.println("Error while posting to ElasticSearch: " + e.toString());
    }

    private void decrementOutstanding() {
      if (outstandingRq.decrementAndGet() == 0) {
        // Yes, in theory someone could have incremented the counter before we grab the monitor,
        // but since we're checking the counter in a loop, we'd catch that.
        synchronized (this) {
          notifyAll();
        }
      }
    }
  }
}
