package com.vmware.vropsexport;

import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.security.CertUtils;
import com.vmware.vropsexport.security.RecoverableCertificateException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.cli.*;
import org.apache.http.HttpException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Scanner;

public abstract class Command {

  private static final int DEFAULT_ROWS_PER_THREAD = 1000;

  protected boolean verbose;
  protected long begin;
  protected long end;
  protected String username;
  protected String password;
  protected String refreshToken;
  protected String output;
  protected String host;
  protected boolean quiet;
  protected int threads = 10;
  protected boolean useTmpFile;
  protected int maxRows;
  protected String trustStore;
  protected String trustPass;
  protected int maxRes;
  protected boolean dumpRest;
  protected Client client;

  protected static String getExceptionMessage(Throwable t) {
    while (t != null && t.getMessage() == null) {
      t = t.getCause();
    }
    return t.getMessage() != null ? t.getMessage() : "No error message available";
  }

  protected abstract void run(CommandLine commandLine) throws ExporterException;

  protected void start(final String[] args) {
    try {
      final CommandLine commandLine = parseOptions(args);
      client = createClient();
      run(commandLine);
    } catch (final ExporterException e) {
      System.err.println("ERROR: " + getExceptionMessage(e));
      System.exit(1);
    } catch (final CertificateException
        | KeyManagementException
        | KeyStoreException
        | NoSuchAlgorithmException e) {
      System.err.println("SSL ERROR: " + getExceptionMessage(e));
      System.exit(1);
    } catch (final IOException | HttpException e) {
      System.err.println("Error connecting to server: " + getExceptionMessage(e));
    }
  }

  protected Client createClient()
      throws CertificateException, IOException, KeyStoreException, NoSuchAlgorithmException,
          ExporterException, HttpException, KeyManagementException {
    for (; ; ) {
      final KeyStore ks = CertUtils.loadExtendedTrust(trustStore, trustPass);
      try {
        final Client client = new Client(host, ks, dumpRest);
        if (refreshToken != null) {
          client.login(refreshToken);
        } else {
          client.login(username, password);
        }
        return client;
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

  protected Options defineOptions() {
    final Options opts = new Options();
    opts.addOption("v", "verbose", false, "Print debug and timing information");
    opts.addOption("l", "lookback", true, "Lookback time");
    opts.addOption("s", "start", true, "Time period start (date format in definition file)");
    opts.addOption("e", "end", true, "Time period end (date format in definition file)");
    opts.addOption("u", "username", true, "Username");
    opts.addOption("p", "password", true, "Password");
    opts.addOption("r", "refreshtoken", true, "Refresh token");
    opts.addOption("o", "output", true, "Output file");
    opts.addOption("H", "host", true, "URL to vRealize Operations Host");
    opts.addOption("q", "quiet", false, "Quiet mode (no progress counter)");
    opts.addOption("t", "threads", true, "Number of parallel processing threads (default=10)");
    opts.addOption("S", "streaming", false, "True streaming processing. Faster but less reliable");
    opts.addOption(
        "m", "max-rows", true, "Maximum number of rows to fetch (default=1000*thread count)");
    opts.addOption("T", "truststore", true, "Truststore filename");
    opts.addOption(null, "trustpass", true, "Truststore password (default=changeit)");
    opts.addOption(null, "resfetch", true, "Resource fetch count (default=1000)");
    opts.addOption(null, "dumprest", false, "Dump rest calls to output");
    opts.addOption(
        null,
        "no-sniextension",
        false,
        "Disable SNI extension. May be needed for very old SSL implementations");
    opts.addOption("h", "help", false, "Print a short help text");
    return opts;
  }

  protected CommandLine parseOptions(final String[] args)
      throws ExporterException, HttpException, IOException, CertificateException, KeyStoreException,
          NoSuchAlgorithmException, KeyManagementException {
    // Parse command line
    final CommandLineParser parser = new DefaultParser();
    final Options opts = defineOptions();
    CommandLine commandLine = null;
    try {
      commandLine = parser.parse(opts, args);
    } catch (final ParseException e) {
      System.err.println(
          "Error parsing command. Use -h option for help. Details: " + getExceptionMessage(e));
      System.exit(1);
    }

    // Help requested?
    if (commandLine.hasOption('h')) {
      final HelpFormatter hf = new HelpFormatter();
      final String head = "Exports vRealize Operations Metrics";
      final String foot = "\nProject home: https://github.com/prydin/vrops-export";
      hf.printHelp("exporttool", head, opts, foot, true);
      System.exit(0);
    }

    // Using refresh token
    refreshToken = commandLine.getOptionValue('r');
    username = commandLine.getOptionValue('u');
    password = commandLine.getOptionValue('p');
    if (refreshToken != null && username != null) {
      throw new ExporterException("Refresh token and user name are mutually exclusive");
    }
    if (refreshToken == null) {
      if (username == null) {
        throw new ExporterException("Username must be specified");
      }

      // Prompt for password if not specified.
      if (password == null) {
        System.err.print("Password: ");
        final char[] chp = System.console().readPassword();
        password = new String(chp);
      }
    }
    host = commandLine.getOptionValue('H');
    if (host == null) {
      throw new ExporterException("Host URL must be specified");
    }
    output = commandLine.getOptionValue('o');
    verbose = commandLine.hasOption('v');
    if (verbose) {
      Configurator.setRootLevel(Level.DEBUG);
    }
    useTmpFile = !commandLine.hasOption('S');
    trustStore = commandLine.getOptionValue('T');
    trustPass = commandLine.getOptionValue("trustpass");
    dumpRest = commandLine.hasOption("dumprest");

    // Disable SNIExtensions if specified. Only needed for very old SSL implementations
    if (commandLine.hasOption("no-sniextension")) {
      System.setProperty("jsse.enableSNIExtension", "false");
    }

    final String mrS = commandLine.getOptionValue('m');
    maxRows = mrS != null ? Integer.parseInt(mrS) : 0;

    // Deal with lookback/time period
    final String lb = commandLine.getOptionValue('l');
    final String startS = commandLine.getOptionValue('s');
    final String endS = commandLine.getOptionValue('e');
    if (lb != null && (endS != null || startS != null)) {
      throw new ExporterException("Lookback and start/end can't be specified at the same time");
    }
    if (startS != null ^ endS != null) {
      throw new ExporterException("Both start and end must be specified");
    }
    quiet = commandLine.hasOption('q');
    String tmp = commandLine.getOptionValue('t');
    if (tmp != null) {
      try {
        threads = Integer.parseInt(tmp);
        if (threads < 1 || threads > 20) {
          throw new ExporterException("Number of threads must greater than 0 and smaller than 20");
        }
      } catch (final NumberFormatException e) {
        throw new ExporterException("Number of threads must be a valid integer");
      }
    }
    // If maxrows isn't specified, default to threads*1000
    if (maxRows == 0) {
      maxRows = threads * DEFAULT_ROWS_PER_THREAD;
    }
    maxRes = maxRows; // Always default to maxrows. Going below that wouldn't make sense.
    tmp = commandLine.getOptionValue("resfetch");
    if (tmp != null) {
      try {
        maxRes = Integer.parseInt(tmp);
        if (maxRes < 1 || maxRes > 50000) {
          throw new ExporterException("Resource fetch must greater than 0 and smaller than 50000");
        }
      } catch (final NumberFormatException e) {
        throw new ExporterException("Resource fetch must be a valid integer");
      }
    }
    // Deal with start and end dates
    end = System.currentTimeMillis();
    final long lbMs = lb != null ? parseLookback(lb) : 1000L * 60L * 60L * 24L;
    begin = end - lbMs;
    if (startS != null) {
      final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm[:ss]");
      try {
        end = df.parse(endS).getTime();
        begin = df.parse(startS).getTime();
      } catch (final java.text.ParseException e) {
        throw new ExporterException(getExceptionMessage(e));
      }
    }
    return commandLine;
  }
}
