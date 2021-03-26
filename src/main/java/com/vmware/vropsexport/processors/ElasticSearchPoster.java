package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;

public class ElasticSearchPoster implements RowsetProcessor {
  private final String url;

  private final RestHighLevelClient client;

  public ElasticSearchPoster(final String url) {
    this.url = url;
    client = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)));
  }

  @Override
  public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {}

  @Override
  public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {}

  @Override
  public void close() throws ExporterException {}
}
