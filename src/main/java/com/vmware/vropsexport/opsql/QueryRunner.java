package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Client;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.Exporter;
import com.vmware.vropsexport.exceptions.ExporterException;
import org.apache.http.HttpException;

import java.io.IOException;
import java.io.OutputStream;

public class QueryRunner {
  private final Client client;

  public QueryRunner(final Client client) {
    this.client = client;
  }

  public void run(final String query, final OutputStream output)
      throws ExporterException, HttpException, IOException {
    final Query q = QueryCompiler.compile(query);
    final Config conf = q.toConfig();
    final Exporter e = new Exporter(client, 3, conf, false, true, 1000, 10000);
    e.exportTo(
        output, System.currentTimeMillis() - 86400000, System.currentTimeMillis(), null, true);
  }
}
