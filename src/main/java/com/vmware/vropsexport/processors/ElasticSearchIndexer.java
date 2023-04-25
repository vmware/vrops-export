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
package com.vmware.vropsexport.processors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import com.vmware.vropsexport.elasticsearch.ElasticSearchConfig;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.json.JsonProducer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.http.ConnectionClosedException;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

public class ElasticSearchIndexer implements RowsetProcessor {
  public static class Factory implements RowsetProcessorFacotry {

    @Override
    public RowsetProcessor makeFromConfig(
        final OutputStream out, final Config config, final DataProvider dp)
        throws ExporterException {
      return new ElasticSearchIndexer(config, dp);
    }

    @Override
    public boolean isProducingOutput() {
      return false;
    }
  }

  private static final Logger log = LogManager.getLogger(ElasticSearchIndexer.class);

  private static final long RETRY_DELAY = 1000;

  private static final long MAX_RETRY_DELAY = 64000;

  private final RestClient client;

  private final String index;

  private final String type;

  private final DataProvider dataProvider;

  private final DateFormat dateFormat;

  private final int bulkSize;

  public ElasticSearchIndexer(final Config config, final DataProvider dataProvider)
      throws ExporterException {
    final ElasticSearchConfig ec = config.getElasticSearchConfig();
    if (ec == null) {
      throw new ExporterException("The configuration is missing the 'elasticSearchConfig' section");
    }
    final List<String> urls = ec.getUrls();
    index = ec.getIndex();
    type = ec.getType();
    final RestClientBuilder builder =
        RestClient.builder(urls.stream().map(HttpHost::create).toArray(HttpHost[]::new));
    if (ec.getApiKey() != null) {
      builder.setDefaultHeaders(
          new Header[] {new BasicHeader("Authorization", "ApiKey " + ec.getApiKey())});
    } else if (ec.getUsername() != null) {
      final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY, new UsernamePasswordCredentials(ec.getUsername(), ec.getPassword()));
      builder.setHttpClientConfigCallback(
          httpClientBuilder ->
              httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
    }
    client = builder.build();

    this.dataProvider = dataProvider;
    dateFormat = config.getDateFormatter();
    bulkSize = ec.getBulkSize();
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) {}

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
    try {
      final AtomicInteger counter = new AtomicInteger();
      final Collection<List<Map.Entry<Long, Row>>> chunks =
          rowset.getRows().entrySet().stream()
              .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / bulkSize))
              .values();
      for (final List<Map.Entry<Long, Row>> chunk : chunks) {
        handleChunk(chunk, rowset, meta);
      }
    } catch (final IOException | HttpException | InterruptedException e) {
      throw new ExporterException(e);
    }
  }

  private void handleChunk(
      final List<Map.Entry<Long, Row>> rows, final Rowset rowset, final RowMetadata meta)
      throws IOException, HttpException, InterruptedException, ExporterException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final JsonFactory jf = new JsonFactory();
    final JsonGenerator generator = jf.createGenerator(out, JsonEncoding.UTF8);
    final JsonProducer producer = new JsonProducer(generator, dataProvider, dateFormat);
    for (final Map.Entry<Long, Row> row : rows) {
      generator.writeStartObject();
      generator.writeObjectFieldStart("index");
      generator.writeStringField("_index", index);
      if (type != null) {
        generator.writeStringField("_type", type);
      }
      generator.writeStringField("_id", UUID.randomUUID().toString());
      generator.writeEndObject();
      generator.writeEndObject();
      generator.flush();
      out.write('\n');
      producer.produceSingleElastic(row.getValue(), row.getKey(), rowset.getResourceId(), meta);
      generator.flush();
      out.write('\n');
    }
    generator.close();
    out.write('\n');
    out.close();
    log.debug(
        "Sumbitting a chunk of "
            + rows.size()
            + " rows ("
            + out.size()
            + " bytes) to ElasticSearch");
    final Request rq = new Request("POST", "/_bulk");
    rq.setEntity(new ByteArrayEntity(out.toByteArray(), ContentType.APPLICATION_JSON));

    long delay = RETRY_DELAY;
    for (; ; ) {
      // Add some fuzz to the delay to make threads run out of synch. Otherwise, we'd have
      // all threads bombarding the backend at the same time, most likely causing us
      // to end up overloading it again.
      final long actualDelay = delay + ThreadLocalRandom.current().nextLong(delay / 2);
      try {
        final long start = System.currentTimeMillis();
        final Response resp = client.performRequest(rq);
        log.debug(
            "Submitting data to ElasticSearch took " + (System.currentTimeMillis() - start) + "ms");
        final StatusLine status = resp.getStatusLine();
        switch (status.getStatusCode()) {
          case 200:
          case 201:
            return;
          case 403:
            if (status.getReasonPhrase().contains("Request throttled")) {
              // We overloaded the cluster. Delay for a while to let it catch up!
              log.warn(
                  "Cluster overloaded (requests throttled). Waiting "
                      + actualDelay
                      + "ms to let it catch up");
              Thread.sleep(actualDelay);
            } else {
              throw new ExporterException(
                  "Error submitting to ElasticSearch: " + status.toString());
            }
            break;
          case 503:
            log.warn(
                "Cluster overloaded (service unavailable). Waiting "
                    + actualDelay
                    + "ms to let it catch up");
            Thread.sleep(actualDelay);
            break;
          default:
            throw new ExporterException("Error submitting to ElasticSearch: " + status.toString());
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
      client.close();
    } catch (final IOException e) {
      throw new ExporterException(e);
    }
  }
}
