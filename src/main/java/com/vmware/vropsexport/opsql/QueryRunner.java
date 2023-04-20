package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Command;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.Exporter;
import com.vmware.vropsexport.exceptions.ExporterException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.http.HttpException;

import java.io.IOException;
import java.io.OutputStream;

public class QueryRunner extends Command {
  @Override
  public void run(final OutputStream out, final CommandLine commandLine) throws ExporterException {
    final String query = commandLine.getOptionValue('Q');
    try {
      executeQuery(query, out);
    } catch (final OpsqlException e) {
      // The error message is already printed at this point, so we can ignore the exception
    }
  }

  public void executeQuery(final String query, final OutputStream out) throws ExporterException {
    final Query q = QueryCompiler.compile(query);
    final Config conf = q.toConfig();
    final Exporter exporter = new Exporter(client, 3, conf, false, true, 1000, 10000);
    try {
      exporter.exportTo(out, begin, end, null, true);
    } catch (final IOException | HttpException e) {
      throw new ExporterException(e);
    }
  }

  @Override
  protected Options defineOptions() {
    final Options opts = super.defineOptions();
    opts.addOption("Q", "query", true, "Literal query");
    return opts;
  }

  public static void main(final String[] args) {
    new QueryRunner().start(args);
  }
}
