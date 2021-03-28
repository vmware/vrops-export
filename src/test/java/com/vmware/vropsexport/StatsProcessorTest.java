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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vmware.vropsexport.processors.CSVPrinter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import org.apache.http.HttpException;
import org.junit.Test;

public class StatsProcessorTest {
  @Test
  public void testProcess() throws IOException, ExporterException, HttpException {
    final DataProvider dp = mock(DataProvider.class);
    when(dp.getResourceName(any())).thenReturn("vm-01");
    final Config conf = ConfigLoader.parse(new FileReader("testdata/vmfields.yaml"));
    final StatsProcessor sp =
        new StatsProcessor(conf, new RowMetadata(conf), dp, new LRUCache<>(1000), null, false);
    final RowsetProcessor rp = new CSVPrinter.Factory().makeFromConfig(System.out, conf, dp);
    sp.process(
        new FileInputStream("testdata/stats.json"),
        rp,
        System.currentTimeMillis() - 300000,
        System.currentTimeMillis());
  }
}
