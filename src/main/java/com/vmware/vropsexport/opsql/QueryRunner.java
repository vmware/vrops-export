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
package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Command;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.Exporter;
import com.vmware.vropsexport.Metadata;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.opsql.console.Console;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.http.HttpException;

import java.io.IOException;
import java.io.OutputStream;

public class QueryRunner extends Command {
  @Override
  public void run(final OutputStream out, final CommandLine commandLine) throws ExporterException {
    if (commandLine.hasOption('Q')) {
      final String query = commandLine.getOptionValue('Q');
      try {
        executeQuery(query, out);
      } catch (final OpsqlException e) {
        // The error message is already printed at this point, so we can ignore the exception
      }
    } else {
      new Console(new Metadata(client)).run(this);
    }
  }

  public void executeQuery(final String query, final OutputStream out) throws ExporterException {
    final Query q = QueryCompiler.compile(query);
    final Config conf = q.toConfig();
    final Exporter exporter = new Exporter(client, threads, conf, verbose, true, maxRows, maxRes);
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
