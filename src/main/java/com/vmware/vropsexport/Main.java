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
package com.vmware.vropsexport;

import com.vmware.vropsexport.exceptions.ExporterException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.http.HttpException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;

public class Main extends Command {
  static {
    final ConfigurationBuilder<BuiltConfiguration> builder =
        ConfigurationBuilderFactory.newConfigurationBuilder();
    final LayoutComponentBuilder layout = builder.newLayout("PatternLayout");
    layout.addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable");
    final AppenderComponentBuilder console = builder.newAppender("stderr", "Console");
    console.add(layout);
    builder.add(console);
    final RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.WARN);
    rootLogger.add(builder.newAppenderRef("stderr"));
    builder.add(rootLogger);
    Configurator.initialize(builder.build());
  }

  public static void main(final String[] args) throws Exception {
    final Main program = new Main();
    program.start(args);
  }

  @Override
  protected CommandLine parseOptions(final String[] args)
      throws ExporterException, HttpException, IOException, CertificateException, KeyStoreException,
          NoSuchAlgorithmException, KeyManagementException {
    final CommandLine commandLine = super.parseOptions(args);
    return commandLine;
  }

  @Override
  public void run(final CommandLine commandLine) throws ExporterException {
    try {
      // If we're just printing field names, we have enough parameters at this point.
      final String resourceKind = commandLine.getOptionValue('F');
      if (resourceKind != null) {
        final Exporter exporter =
            createExporter(client, threads, null, verbose, useTmpFile, 5000, 1000);
        exporter.printResourceMetadata(resourceKind, System.out);
      } else if (commandLine.hasOption('R')) {
        final String adapterKind = commandLine.getOptionValue('R');
        final Exporter exporter =
            createExporter(client, threads, null, verbose, useTmpFile, 5000, 1000);
        exporter.printResourceKinds(adapterKind, System.out);
      } else if (commandLine.hasOption('A')) {
        final Exporter exporter =
            createExporter(client, threads, null, verbose, useTmpFile, 5000, 1000);
        exporter.printAdapterKinds(System.out);
      } else if (commandLine.hasOption('G')) {
        final String rk = commandLine.getOptionValue('G');
        final Exporter exporter =
            createExporter(client, threads, null, verbose, useTmpFile, 5000, 1000);
        exporter.generateExportDefinition(rk, System.out);
      } else {
        final String defFile = commandLine.getOptionValue('d');
        if (defFile == null) {
          throw new ExporterException("Definition file must be specified");
        }
        final String namePattern = commandLine.getOptionValue('n');
        final String parentSpec = commandLine.getOptionValue('P');
        if (namePattern != null && parentSpec != null) {
          throw new ExporterException("Name filter is not supported with parent is specified");
        }

        // Read definition and run it!
        try (final Reader fr =
            new InputStreamReader(new FileInputStream(defFile), StandardCharsets.UTF_8)) {
          final Config conf = ConfigLoader.parse(fr);

          // Output to stdout implies quiet mode. Also, verbose would mess up the progress counter,
          // so turn it off.
          // If we're outputting to a textual format that can dump to stdout, we supress the
          // progress counter, but
          // if we're dumping to e.g. SQL, we keep it on. This is a bit kludgy.. TODO: Revisit
          if (output == null && Exporter.isProducingOutput(conf) || verbose) {
            quiet = true;
          }

          final Exporter exporter =
              createExporter(client, threads, conf, verbose, useTmpFile, maxRows, maxRes);
          if (namePattern != null) {
            conf.getQuery().setName(Collections.singletonList(namePattern));
          }
          if (output == null) {
            exporter.exportTo(System.out, begin, end, parentSpec, quiet);
          } else {
            try (final OutputStream out = new FileOutputStream(output)) {
              exporter.exportTo(out, begin, end, parentSpec, quiet);
            }
          }
        }
      }
    } catch (final Exception e) {
      throw new ExporterException(e);
    }
  }

  private static Exporter createExporter(
      final Client client,
      final int threads,
      final Config conf,
      final boolean verbose,
      final boolean useTempFile,
      final int maxRows,
      final int maxRes)
      throws ExporterException {
    return new Exporter(client, threads, conf, verbose, useTempFile, maxRows, maxRes);
  }

  @Override
  protected Options defineOptions() {
    final Options opts = super.defineOptions();
    opts.addOption("d", "definition", true, "Path to definition file");
    opts.addOption("n", "namequery", true, "Name query");
    opts.addOption("P", "parent", true, "Parent resource (ResourceKind:resourceName)");
    opts.addOption("R", "resource-kinds", true, "List resource kinds");
    opts.addOption("A", "adapter-kinds", false, "List adapter kinds");
    return opts;
  }
}
