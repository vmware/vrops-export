/* 
 * Copyright 2017 Pontus Rydin
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
package net.virtualviking.vropsexport.processors;

import java.util.List;

import net.virtualviking.vropsexport.Config;
import net.virtualviking.vropsexport.ExporterException;
import net.virtualviking.vropsexport.LRUCache;
import net.virtualviking.vropsexport.Row;
import net.virtualviking.vropsexport.RowMetadata;
import net.virtualviking.vropsexport.Rowset;
import net.virtualviking.vropsexport.RowsetProcessor;

public class ParentSplicer implements RowsetProcessor {
	
	private final Rowset childRowset; 
	
	private final LRUCache<String, Rowset> rowsetCache;
	
	private final String cacheKey;

	public ParentSplicer(Rowset childRowset, LRUCache<String, Rowset> rowsetCache, String cacheKey) {
		super();
		this.childRowset = childRowset;
		this.rowsetCache = rowsetCache;
		this.cacheKey = cacheKey;
	}

	@Override
	public void preamble(RowMetadata meta, Config conf) throws ExporterException {
		// Nothing to do...
	}
	
	@Override
	public void process(Rowset rowset, RowMetadata meta) throws ExporterException {
		spliceRows(childRowset, rowset);
		synchronized(this.rowsetCache) {
			this.rowsetCache.put(cacheKey, rowset);
		}
	}
	
	public static void spliceRows(Rowset child, Rowset parent) {
		for(Row pRow : parent.getRows().values()) {
			Row cRow = child.getRows().get(pRow.getTimestamp());
			if(cRow != null) {
				for(int j = 0; j < pRow.getNumMetrics(); ++j) {
					Double d = pRow.getMetric(j);
					if(d != null)
						cRow.setMetric(j, d);
				}
				for(int j = 0; j < pRow.getNumProps(); ++j) {
					String s = pRow.getProp(j);
					if(s != null)
						cRow.setProp(j, s);
				}
			}
		}
	}
}
