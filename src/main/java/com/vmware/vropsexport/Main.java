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

import com.vmware.vropsexport.security.CertUtils;
import com.vmware.vropsexport.security.RecoverableCertificateException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Scanner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.HttpException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class Main {
  static {
    final ConfigurationBuilder<BuiltConfiguration> builder =
        ConfigurationBuilderFactory.newConfigurationBuilder();
    final LayoutComponentBuilder layout = builder.newLayout("PatternLayout");
    layout.addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable");
    final AppenderComponentBuilder console = builder.newAppender("stdout", "Console");
    console.add(layout);
    builder.add(console);
    final RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.WARN);
    rootLogger.add(builder.newAppenderRef("stdout"));
    builder.add(rootLogger);
    Configurator.initialize(builder.build());
  }

  private static final int DEFAULT_ROWS_PER_THREAD = 1000;

  public static void main(final String[] args) throws Exception {
    // Parse command line
    //
    final CommandLineParser parser = new DefaultParser();
    final Options opts = defineOptions();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(opts, args);
    } catch (final ParseException e) {
      System.err.println(
          "Error parsing command. Use -h option for help. Details: " + e.getMessage());
      System.exit(1);
    }
    // Help requested?
    //
    if (commandLine.hasOption('h')) {
      final HelpFormatter hf = new HelpFormatter();
      final String head = "Exports vRealize Operations Metrics";
      final String foot = "\nProject home: https://github.com/prydin/vrops-export";
      hf.printHelp("exporttool", head, opts, foot, true);
      System.exit(0);
    }
    try {
      // Extract command options and do sanity checks.
      //
      int threads = 10;
      final String username = commandLine.getOptionValue('u');
      if (username == null) {
        throw new ExporterException("Username must be specified");
      }
      String password = commandLine.getOptionValue('p');

      // Prompt for password if not specified.
      //
      if (password == null) {
        System.err.print("Password: ");
        final char[] chp = System.console().readPassword();
        password = new String(chp);
      }
      final String host = commandLine.getOptionValue('H');
      if (host == null) {
        throw new ExporterException("Host URL must be specified");
      }
      final String output = commandLine.getOptionValue('o');
      if (commandLine.hasOption('i')) {
        throw new ExporterException("Trusting all certs is no longer supported");
      }
      final boolean verbose = commandLine.hasOption('v');
      if (verbose) {
        Configurator.setRootLevel(Level.DEBUG);
      }
      final boolean useTmpFile = !commandLine.hasOption('S');
      final String trustStore = commandLine.getOptionValue('T');
      final String trustPass = commandLine.getOptionValue("trustpass");
      final boolean dumpRest = commandLine.hasOption("dumprest");

      // If we're just printing field names, we have enough parameters at this point.
      //
      final String resourceKind = commandLine.getOptionValue('F');
      if (resourceKind != null) {
        final Exporter exporter =
            createExporter(
                host,
                username,
                password,
                threads,
                null,
                verbose,
                dumpRest,
                useTmpFile,
                5000,
                1000,
                trustStore,
                trustPass);
        exporter.printResourceMetadata(resourceKind, System.out);
      } else if (commandLine.hasOption('R')) {
        final String adapterKind = commandLine.getOptionValue('R');
        final Exporter exporter =
            createExporter(
                host,
                username,
                password,
                threads,
                null,
                verbose,
                dumpRest,
                useTmpFile,
                5000,
                1000,
                trustStore,
                trustPass);
        exporter.printResourceKinds(adapterKind, System.out);
      } else if (commandLine.hasOption('G')) {
        final String rk = commandLine.getOptionValue('G');
        final Exporter exporter =
            createExporter(
                host,
                username,
                password,
                threads,
                null,
                verbose,
                dumpRest,
                useTmpFile,
                5000,
                1000,
                trustStore,
                trustPass);
        exporter.generateExportDefinition(rk, System.out);
      } else {
        final String defFile = commandLine.getOptionValue('d');
        if (defFile == null) {
          throw new ExporterException("Definition file must be specified");
        }
        final String mrS = commandLine.getOptionValue('m');
        int maxRows = mrS != null ? Integer.parseInt(mrS) : 0;

        // Deal with lookback/time period
        //
        final String lb = commandLine.getOptionValue('l');
        final String startS = commandLine.getOptionValue('s');
        final String endS = commandLine.getOptionValue('e');
        if (lb != null && (endS != null || startS != null)) {
          throw new ExporterException("Lookback and start/end can't be specified at the same time");
        }
        if (startS != null ^ endS != null) {
          throw new ExporterException("Both start and end must be specified");
        }
        final String namePattern = commandLine.getOptionValue('n');
        final String parentSpec = commandLine.getOptionValue('P');
        if (namePattern != null && parentSpec != null) {
          throw new ExporterException("Name filter is not supported with parent is specified");
        }
        boolean quiet = commandLine.hasOption('q');
        String tmp = commandLine.getOptionValue('t');
        if (tmp != null) {
          try {
            threads = Integer.parseInt(tmp);
            if (threads < 1 || threads > 20) {
              throw new ExporterException(
                  "Number of threads must greater than 0 and smaller than 20");
            }
          } catch (final NumberFormatException e) {
            throw new ExporterException("Number of threads must be a valid integer");
          }
        }
        // If maxrows isn't specified, default to threads*1000
        if (maxRows == 0) {
          maxRows = threads * DEFAULT_ROWS_PER_THREAD;
        }
        int maxRes = maxRows; // Always default to maxrows. Going below that wouldn't make sense.
        tmp = commandLine.getOptionValue("resfetch");
        if (tmp != null) {
          try {
            maxRes = Integer.parseInt(tmp);
            if (threads < 1 || threads > 50000) {
              throw new ExporterException(
                  "Resource fetch must greater than 0 and smaller than 50000");
            }
          } catch (final NumberFormatException e) {
            throw new ExporterException("Resource fetch must be a valid integer");
          }
        }

        // Read definition and run it!
        //
        try (final Reader fr = new InputStreamReader(new FileInputStream(defFile), "UTF-8")) {
          final Config conf = ConfigLoader.parse(fr);

          // Output to stdout implies quiet mode. Also, verbose would mess up the progress counter,
          // so turn it off.
          // If we're outputting to a textual format that can dump to stdout, we supress the
          // progress counter, but
          // if we're dumping to e.g. SQL, we keep it on. This is a bit kludgy.. TODO: Revisit
          //
          if (output == null && Exporter.isProducingOutput(conf) || verbose) {
            quiet = true;
          }

          // Deal with start and end dates
          //
          long end = System.currentTimeMillis();
          final long lbMs = lb != null ? parseLookback(lb) : 1000L * 60L * 60L * 24L;
          long begin = end - lbMs;
          if (startS != null) {
            if (conf.getDateFormat() == null) {
              throw new ExporterException(
                  "Date format must be specified in config file if -e and -s are used");
            }
            final DateFormat df = new SimpleDateFormat(conf.getDateFormat());
            try {
              end = df.parse(endS).getTime();
              begin = df.parse(startS).getTime();
            } catch (final java.text.ParseException e) {
              throw new ExporterException(e.getMessage());
            }
          }
          final Exporter exporter =
              createExporter(
                  host,
                  username,
                  password,
                  threads,
                  conf,
                  verbose,
                  dumpRest,
                  useTmpFile,
                  maxRows,
                  maxRes,
                  trustStore,
                  trustPass);
          try (final OutputStream out =
              output != null ? new FileOutputStream(output) : System.out) {
            exporter.exportTo(out, begin, end, namePattern, parentSpec, quiet);
          }
        }
      }
    } catch (final RecoverableCertificateException e) {
      System.err.println("SSL ERROR: " + e.getMessage() + "\n\nConsider using the -i option!");
      System.exit(1);
    } catch (final ExporterException e) {
      System.err.println("ERROR: " + e.getMessage());
      System.exit(1);
    }
  }

  private static Exporter createExporter(
      final String urlBase,
      final String username,
      final String password,
      final int threads,
      final Config conf,
      final boolean verbose,
      final boolean dumpRest,
      final boolean useTempFile,
      final int maxRows,
      final int maxRes,
      final String trustStore,
      final String trustPass)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException,
          KeyManagementException, HttpException, ExporterException {
    for (; ; ) {
      final KeyStore ks = CertUtils.loadExtendedTrust(trustStore, trustPass);
      try {
        return new Exporter(
            urlBase,
            username,
            password,
            threads,
            conf,
            verbose,
            dumpRest,
            useTempFile,
            maxRows,
            maxRes,
            ks);
      } catch (final RecoverableCertificateException e) {
        final boolean retry = promptForTrust(e.getCapturedCerts()[0], trustStore, trustPass);
        if (!retry) {
          throw e;
        }
      }
    }
  }

  private static boolean promptForTrust(
      final X509Certificate serverCert, final String trustStore, final String trustPass)
      throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
    System.err.println(
        "Certificate is not trusted. Thumbprint: " + CertUtils.getThumbprint(serverCert, true));
    System.err.println("Issuer: " + serverCert.getIssuerDN().toString());
    System.err.println("Subject: " + serverCert.getSubjectDN().toString());
    System.err.print("Do you want to permanently trust this certificate? (y/n): ");
    final Scanner s = new Scanner(System.in, "UTF-8");
    final String answer = s.nextLine();
    System.err.println();
    if (answer.equalsIgnoreCase("y")) {
      CertUtils.storeCert(serverCert, trustStore, trustPass);
      return true;
    }
    return false;
  }

  private static Options defineOptions() {

    final Options opts = new Options();
    opts.addOption("v", "verbose", false, "Print debug and timing information");
    opts.addOption("d", "definition", true, "Path to definition file");
    opts.addOption("l", "lookback", true, "Lookback time");
    opts.addOption("s", "start", true, "Time period start (date format in definition file)");
    opts.addOption("e", "end", true, "Time period end (date format in definition file)");
    opts.addOption("n", "namequery", true, "Name query");
    opts.addOption("P", "parent", true, "Parent resource (ResourceKind:resourceName)");
    opts.addOption("u", "username", true, "Username");
    opts.addOption("p", "password", true, "Password");
    opts.addOption("o", "output", true, "Output file");
    opts.addOption("H", "host", true, "URL to vRealize Operations Host");
    opts.addOption("q", "quiet", false, "Quiet mode (no progress counter)");
    opts.addOption("i", "ignore-cert", false, "Trust any cert (DEPRECATED!)");
    opts.addOption("F", "list-fields", true, "Print name and keys of all fields to stdout");
    opts.addOption("t", "threads", true, "Number of parallel processing threads (default=10)");
    opts.addOption("S", "streaming", false, "True streaming processing. Faster but less reliable");
    opts.addOption("R", "resource-kinds", true, "List resource kinds");
    opts.addOption(
        "m", "max-rows", true, "Maximum number of rows to fetch (default=1000*thread count)");
    opts.addOption("T", "truststore", true, "Truststore filename");
    opts.addOption("G", "generate", true, "Generate template definition for resource type");
    opts.addOption(null, "trustpass", true, "Truststore password (default=changeit)");
    opts.addOption(null, "resfetch", true, "Resource fetch count (default=1000)");
    opts.addOption(null, "dumprest", false, "Dump rest calls to output");
    opts.addOption("h", "help", false, "Print a short help text");
    return opts;
  }

  @SuppressFBWarnings("SF_SWITCH_FALLTHROUGH")
  private static long parseLookback(final String lb) throws ExporterException {
    long scale = 1;
    final char unit = lb.charAt(lb.length() - 1);
    switch (unit) {
      case 'd':
        scale *= 24; // fallthru
      case 'h':
        scale *= 60; // fallthru
      case 'm':
        scale *= 60; // fallthru
      case 's':
        scale *= 1000;
        break;
      default:
        throw new ExporterException("Cannot parse time unit");
    }
    try {
      final long t = Long.parseLong(lb.substring(0, lb.length() - 1));
      return t * scale;
    } catch (final NumberFormatException e) {
      throw new ExporterException("Cannot parse time value");
    }
  }
}
