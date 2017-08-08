/* 
 * Copyright 2017 VMware, Inc. All RIghts Reserved
 *
 * SPDX-License-Identifier: Apache-2.0.
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

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;

import org.apache.http.HttpException;
import org.junit.Test;

import net.virtualviking.vropsexport.Config;
import net.virtualviking.vropsexport.ConfigLoader;
import net.virtualviking.vropsexport.ExporterException;
import net.virtualviking.vropsexport.StatsProcessor;
import net.virtualviking.vropsexport.processors.CSVPrinter;

public class TestStatsProcessor {
	@Test
	public void testProcess() throws IOException, ExporterException, HttpException  {
		Config conf = ConfigLoader.parse(new FileReader("testdata/vmfields.yaml"));
		StatsProcessor sp = new StatsProcessor(conf, null, new LRUCache<>(10), false);
		RowsetProcessor rp = new CSVPrinter(new BufferedWriter(new OutputStreamWriter(System.out)), new SimpleDateFormat("yyy-MM-dd HH:mm"), null, null, null);
		sp.process(new FileInputStream("testdata/stats.json"), rp, System.currentTimeMillis() - 300000, System.currentTimeMillis());
	}
}
