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
package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.CSVConfig;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.ExporterException;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.vmware.vropsexport.RowMetadata;

import org.apache.http.HttpException;

import java.util.Date;
import java.util.Iterator;

import com.vmware.vropsexport.Row;
import java.io.BufferedWriter;

import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;

@SuppressWarnings("WeakerAccess")
public class CSVPrinter implements RowsetProcessor {

	public static class Factory implements RowsetProcessorFacotry {
		@Override
		public RowsetProcessor makeFromConfig(BufferedWriter bw, Config config, DataProvider dp) {
			return new CSVPrinter(bw, new SimpleDateFormat(config.getDateFormat()), config.getCsvConfig(), dp);
		}

		@Override
		public boolean isProducingOutput() {
			return true;
		}
	}

	private final BufferedWriter bw;

	private final DateFormat df;

	private final DataProvider dp;

	private final CSVConfig csvConfig;

	public CSVPrinter(BufferedWriter bw, DateFormat df, CSVConfig csvConfig, DataProvider dp) {
		this.bw = bw;
		this.df = df;
		this.dp = dp;

		// Create a default CSVConfig if none was specified.
		//
		this.csvConfig = csvConfig != null ? csvConfig : new CSVConfig(); 
	}

	@Override
	public void preamble(RowMetadata meta, Config conf) throws ExporterException {
		// If header is suppressed, do nothing...
		//
		if(!csvConfig.isHeader())
			return;
		try {
			// Output table header
			//
			bw.write("timestamp,resName");
			for (Config.Field fld : conf.getFields()) {
				bw.write(",");
				bw.write(fld.getAlias());
			}
			bw.newLine();
		} catch(IOException e) {
			throw new ExporterException(e);
		}
	}

	@Override
	public void process(Rowset rowset, RowMetadata meta) throws ExporterException {
		try {
			synchronized (bw) {
				for (Row row : rowset.getRows().values()) {
					long t = row.getTimestamp();
					if (df != null) {
						bw.write("\"" + df.format(new Date(t)) + "\"");
					} else
						bw.write("\"" + t + "\"");
					bw.write(csvConfig.getDelimiter());
					bw.write("\"");
					bw.write(dp.getResourceName(rowset.getResourceId()));
					bw.write("\"");
					Iterator<Object> itor = row.iterator(meta);
					while (itor.hasNext()) {
						Object o = itor.next();
						bw.write(csvConfig.getDelimiter());
						bw.write("\"");
						bw.write(o != null ? o.toString() : "");
						bw.write('"');
					}
					bw.newLine();
					bw.flush();
				}
			}
		} catch (IOException | HttpException e) {
			throw new ExporterException(e);
		}
	}
}
