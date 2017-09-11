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

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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

import com.vmware.vropsexport.security.CertUtils;
import com.vmware.vropsexport.security.RecoverableCertificateException;

public class Main {
	public static void main(String[] args) throws Exception {
		// Parse command line
		//
		CommandLineParser parser = new DefaultParser();
		Options opts = defineOptions();
		CommandLine commandLine = null;
		try {
			commandLine = parser.parse(opts, args);
		} catch(ParseException e) {
			System.err.println("Error parsing command. Use -h option for help. Details: " + e.getMessage());
			System.exit(1);
		}
		// Help requested?
		//
		if(commandLine.hasOption('h')) {
			HelpFormatter hf = new HelpFormatter();
			String head = "Exports vRealize Operations Metrics";
			String foot = "\nProject home: https://github.com/prydin/vrops-export";
			hf.printHelp("exporttool", head, opts, foot, true);
			System.exit(0);
		}
		try {
			// Extract command options and do sanity checks.
			//
			int threads = 10;
			String username = commandLine.getOptionValue('u');
			if(username == null)
				throw new ExporterException("Username must be specified");
			String password = commandLine.getOptionValue('p');
			
			// Prompt for password if not specified.
			//
			if(password == null) {
				System.err.print("Password: ");
				char[] chp = System.console().readPassword();
				password = new String(chp);
			}
			String host = commandLine.getOptionValue('H');
			if(host == null)
				throw new ExporterException("Host URL must be specified");
			String output = commandLine.getOptionValue('o');
			if(commandLine.hasOption('i')) 
				throw new ExporterException("Trusting all certs is no longer supported");
			boolean verbose = commandLine.hasOption('v');
			boolean useTmpFile = !commandLine.hasOption('S');
			String trustStore = commandLine.getOptionValue('T');
			String trustPass = commandLine.getOptionValue("trustpass");

			// If we're just printing field names, we have enough parameters at this point.
			//
			String resourceKind = commandLine.getOptionValue('F');
			if(resourceKind != null) {
				Exporter exporter = createExporter(host, username, password, threads, null, verbose, useTmpFile, 5000, 1000, trustStore, trustPass);
				exporter.printResourceMetadata(resourceKind, System.out);
			} else if(commandLine.hasOption('R')) {
				String adapterKind = commandLine.getOptionValue('R');
				Exporter exporter = createExporter(host, username, password, threads, null, verbose, useTmpFile, 5000, 1000, trustStore, trustPass);
				exporter.printResourceKinds(adapterKind, System.out);
			} else {	
				String defFile = commandLine.getOptionValue('d');
				if(defFile == null) 
					throw new ExporterException("Definition file must be specified");
				String mrS = commandLine.getOptionValue('m');
				int maxRows = mrS != null ? Integer.parseInt(mrS) : 0;

				// Deal with lookback/time period
				//
				String lb = commandLine.getOptionValue('l');
				String startS = commandLine.getOptionValue('s');
				String endS = commandLine.getOptionValue('e');
				if(lb != null && (endS != null || startS != null)) 
					throw new ExporterException("Lookback and start/end can't be specified at the same time");
				if(startS != null ^ endS != null)	    			
					throw new ExporterException("Both start and end must be specified");
				String namePattern = commandLine.getOptionValue('n');
				String parentSpec = commandLine.getOptionValue('P');
				if(namePattern != null && parentSpec != null) 
					throw new ExporterException("Name filter is not supported with parent is specified");
				boolean quiet = commandLine.hasOption('q');
				String tmp = commandLine.getOptionValue('t');
				if(tmp != null) {
					try {
						threads = Integer.parseInt(tmp);
						if(threads < 1 || threads > 20) {
							throw new ExporterException("Number of threads must greater than 0 and smaller than 20");
						}
					} catch(NumberFormatException e) {
						throw new ExporterException("Number of threads must be a valid integer");
					}
				}
				int maxRes = maxRows; // Always default to maxrows. Going below that wouldn't make sense.
				tmp = commandLine.getOptionValue("resfetch");
				if(tmp != null) {
					try {
						maxRes = Integer.parseInt(tmp);
						if(threads < 1 || threads > 50000) {
							throw new ExporterException("Resource fetch must greater than 0 and smaller than 50000");
						}
					} catch(NumberFormatException e) {
						throw new ExporterException("Resource fetch must be a valid integer");
					}
				}

				// Read definition and run it!
				//
				try (FileReader fr = new FileReader(defFile)) {
					Config conf = ConfigLoader.parse(fr);

					// Output to stdout implies quiet mode. Also, verbose would mess up the progress counter, so turn it off.
					// If we're outputting to a textual format that can dump to stdout, we supress the progress counter, but
					// if we're dumping to e.g. SQL, we keep it on. This is a bit kludgy.. TODO: Revisit
					//
					if (output == null && Exporter.isProducingOutput(conf) || verbose)
						quiet = true;

					// Deal with start and end dates
					//
					long end = System.currentTimeMillis();
					long lbMs = lb != null ? parseLookback(lb) : 1000L * 60L * 60L * 24L;
					long begin = end - lbMs;
					if (startS != null) {
						if (conf.getDateFormat() == null)
							throw new ExporterException("Date format must be specified in config file if -e and -s are used");
						DateFormat df = new SimpleDateFormat(conf.getDateFormat());
						try {
							end = df.parse(endS).getTime();
							begin = df.parse(startS).getTime();
						} catch (java.text.ParseException e) {
							throw new ExporterException(e.getMessage());
						}
					}
					Exporter exporter = createExporter(host, username, password, threads, conf, verbose, useTmpFile, maxRows, maxRes, trustStore, trustPass);
					Writer wrt = output != null ? new FileWriter(output) : new OutputStreamWriter(System.out);
					exporter.exportTo(wrt, begin, end, namePattern, parentSpec, quiet);

				}
			}
		} catch(RecoverableCertificateException e) {
			System.err.println("SSL ERROR: " + e.getMessage() + "\n\nConsider using the -i option!");
			System.exit(1);
		} catch(ExporterException e) {
			System.err.println("ERROR: " + e.getMessage());
			System.exit(1);
		}
	}

	private static Exporter createExporter(String urlBase, String username, String password, int threads, Config conf, boolean verbose, boolean useTempFile, int maxRows, int maxRes, String trustStore, String trustPass) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, KeyManagementException, HttpException, ExporterException
	{
		for(;;) {
			KeyStore ks = CertUtils.loadExtendedTrust(trustStore, trustPass);
			try {
				return new Exporter(urlBase, username, password, threads, conf, verbose, useTempFile, maxRows, maxRes, ks);
			} catch(RecoverableCertificateException e) {
				boolean retry = promptForTrust(e.getCapturedCerts()[0], trustStore, trustPass);
				if(!retry)
					throw e;
			}
		}
	}

	private static boolean promptForTrust(X509Certificate serverCert, String trustStore, String trustPass) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
		System.err.println("Certificate is not trusted. Thumbprint: " + CertUtils.getThumbprint(serverCert, true));
		System.err.println("Issuer: " + serverCert.getIssuerDN().toString());
		System.err.println("Subject: " + serverCert.getSubjectDN().toString());
		System.err.print("Do you want to permanently trust this certificate? (y/n): ");
		Scanner s = new Scanner(System.in);
		String answer = s.nextLine();
		System.err.println();
		if(answer.toLowerCase().equals("y")) {
			CertUtils.storeCert(serverCert, trustStore, trustPass);
			return true;
		}
		return false;
	}

	private static Options defineOptions() {

		Options opts = new Options();
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
		opts.addOption("m", "max-rows", true, "Maximum number of rows to fetch from API (default=unlimited)");
		opts.addOption("T", "truststore", true, "Truststore filename");
		opts.addOption(null, "trustpass", true, "Truststore password (default=changeit)");
		opts.addOption(null, "resfetch", true, "Resource fetch count (default=1000)");
		opts.addOption("h", "help", false, "Print a short help text");
		return opts;
	}		

	private static long parseLookback(String lb) throws ExporterException {
		long scale = 1;
		char unit = lb.charAt(lb.length() - 1);
		switch(unit) {
		case 'd':
			scale *= 24;
		case 'h':
			scale *= 60;
		case 'm':
			scale *= 60;
		case 's':
			scale *= 1000;
			break;
		default:
			throw new ExporterException("Cannot parse time unit");
		}
		try {
			long t = Long.parseLong(lb.substring(0, lb.length() - 1));
			return t * scale;
		} catch(NumberFormatException e) {
			throw new ExporterException("Cannot parse time value");
		}
	}
}
