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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vmware.vropsexport.processors.ParentSplicer;
import org.apache.http.HttpException;
import org.json.JSONObject;

import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class StatsProcessor {
	private final Config conf;
	
	private final RowMetadata rowMetadata;
	
	private final DataProvider dataProvider;
	
	private final LRUCache<String, Rowset> rowsetCache;
	
	private final boolean verbose;
	
	public StatsProcessor(Config conf, DataProvider propertyProvider, LRUCache<String, Rowset> rowsetCache, boolean verbose) throws ExporterException {
		this.conf = conf;
		this.rowMetadata = new RowMetadata(conf);
		this.dataProvider = propertyProvider;
		this.rowsetCache = rowsetCache;
		this.verbose = verbose;
	}
	
	private StatsProcessor(Config conf, RowMetadata rowMetadata, DataProvider propertyProvider, LRUCache<String, Rowset> rowsetCache, boolean verbose) {
		this.conf = conf;
		this.rowMetadata = rowMetadata;
		this.dataProvider = propertyProvider;
		this.rowsetCache = rowsetCache;
		this.verbose = verbose;
	}

	public void process(InputStream is, RowsetProcessor proc, long begin, long end) throws ExporterException, JsonParseException, IOException, HttpException {
		JsonParser p = new JsonFactory().createParser(is);
		
		// Process values { [ ...
		//
		this.expect(p, JsonToken.START_OBJECT);
		this.expect(p, "values");
		this.expect(p, JsonToken.START_ARRAY);
		while(p.nextToken() != JsonToken.END_ARRAY) {
			this.expectCurrent(p, JsonToken.START_OBJECT);
			this.expect(p, "resourceId");
			String resourceId = p.nextTextValue();
			
			// Process stat-list { stat [ ...
			//
			this.expect(p, "stat-list");
			this.expect(p, JsonToken.START_OBJECT);
 			this.expect(p, "stat");
 			this.expect(p, JsonToken.START_ARRAY);
			TreeMap<Long, Row> rows = new TreeMap<>();
 			while(p.nextToken() != JsonToken.END_ARRAY) {
 				
 				// Process timestamps[ ...
 				//
 				this.expectCurrent(p, JsonToken.START_OBJECT);
 				this.expect(p, "timestamps");
 				this.expect(p, JsonToken.START_ARRAY);
 				List<Long> timestamps = new ArrayList<>();
 				while(p.nextToken() != JsonToken.END_ARRAY) {
 					long ts = p.getLongValue();
 					timestamps.add(ts);
 				}
	 			this.expect(p, "statKey");
	 			this.expect(p, JsonToken.START_OBJECT);
	 			this.expect(p,  "key");
	 			String statKey = p.nextTextValue();
	 			this.expect(p, JsonToken.END_OBJECT);

	 			// Keep skipping members until we've found the data node
				//
				while(!expectMaybe(p, "data")) {
					this.skipMember(p, null);
				}
	 			
	 			// Process data[ ...
	 			//
	 			this.expect(p, JsonToken.START_ARRAY);
	 			int metricIdx = rowMetadata.getMetricIndex(statKey);
	 			int i = 0;
				while(p.nextToken() != JsonToken.END_ARRAY) {
					double d = p.getDoubleValue();
					if(metricIdx != -1) {
						long ts = timestamps.get(i++);
						Row r = rows.get(ts);
						if(r == null) {
							r = rowMetadata.newRow(ts);
							rows.put(ts, r);
						}
						r.setMetric(metricIdx, d);
					}
				}
				this.expect(p, JsonToken.END_OBJECT);
 			}
			
			// End of stat-list and values object
			//
			this.expect(p, JsonToken.END_OBJECT);
			this.expect(p, JsonToken.END_OBJECT);
			Rowset rs = new Rowset(resourceId, rows);
			rows = null;
			
			// Splice in properties
			//
			if(dataProvider != null) {
				if(rowMetadata.hasProperties()) {
					// Put in resource id if requested.
					//
					int idIdx = rowMetadata.getPropertyIndex("$resId");
					if(idIdx != -1) {
						for(Row row : rs.getRows().values()) 
							row.setProp(idIdx, resourceId);
					}
					
					// Put in name if requested
					//
					int nameIdx = rowMetadata.getPropertyIndex("$resName");
					if(nameIdx != -1) {
						String name = dataProvider.getResourceName(resourceId);
						for(Row row : rs.getRows().values()) 
							row.setProp(nameIdx, name);
					}
					
					// Splice in properties
					//
					if(rowMetadata.needsPropertyLoad()) {
						Map<String, String> props = dataProvider.fetchProps(resourceId);
						for (Map.Entry<String, String> e : props.entrySet()) {
							int idx = rowMetadata.getPropertyIndex(e.getKey());
							if (idx != -1) {
								for (Row row : rs.getRows().values()) {
									row.setProp(idx, e.getValue());
								}
							}
						}
					}
				}
				
				// Splice in data from parent
				//
				RowMetadata pm = rowMetadata.forParent();
				if(pm.isValid()) {
					long now = System.currentTimeMillis();
					JSONObject parent = dataProvider.getParentOf(resourceId, pm.getResourceKind());
					if(parent != null) {
						Rowset cached = null;
						String cacheKey = parent.getString("identifier") + "|" + begin + "|" + end;
						synchronized(this.rowsetCache) {
							cached = this.rowsetCache.get(cacheKey);
						}
						// Try cache first! Chances are we've seen this parent many times.
						//
						if(cached != null) {
							ParentSplicer.spliceRows(rs, cached);
						} else {
							// Not in cache. Fetch it the hard (and slow) way!
							//
							if(verbose)
								System.err.println("Cache miss for parent " + parent.getJSONObject("resourceKey").getString("name"));
							StatsProcessor parentProcessor = new StatsProcessor(this.conf, pm, this.dataProvider, this.rowsetCache, verbose);
							InputStream pIs = this.dataProvider.fetchMetricStream(Collections.singletonList(parent), pm, begin, end);
							try {
								parentProcessor.process(pIs, new ParentSplicer(rs, this.rowsetCache, cacheKey), begin, end);
							} finally {
								pIs.close();
							}
						}
					}
					if(verbose)
						System.err.println("Parent processing took " + (System.currentTimeMillis() - now));
				}
			}
			if(verbose)
				System.err.println("Processed " + rs.getRows().size() + " rows. Memory used: " + 
						Runtime.getRuntime().totalMemory() + " max=" + Runtime.getRuntime().maxMemory());
			proc.process(rs, rowMetadata);
		}
		this.expect(p, JsonToken.END_OBJECT);
	}
	
	private void expect(JsonParser p, JsonToken token) throws ExporterException, IOException {
		JsonToken t = p.nextToken();
		if(t != token) {
			throw new ExporterException("Expected token " + token.asString() + ", got " + t.asString());
		}
	}
	
	private void expectCurrent(JsonParser p, JsonToken token) throws ExporterException, IOException {
		JsonToken t = p.currentToken();
		if(t != token) {
			throw new ExporterException("Expected token " + token.asString() + ", got " + t.asString());
		}
	}
	
	private void expect(JsonParser p, String fieldname) throws ExporterException, IOException {
		if(!expectMaybe(p, fieldname)) {
			throw new ExporterException("Expected field name " + fieldname + ", got " + p.getCurrentName());
		}
	}

	private boolean expectMaybe(JsonParser p, String fieldname) throws ExporterException, IOException {
		p.nextToken();
		return fieldname == null || fieldname.equals(p.getCurrentName());
	}
	private void expectCurrent(JsonParser p, String fieldname) throws ExporterException, IOException {
		if(!fieldname.equals(p.getCurrentName())) {
			throw new ExporterException("Expected field name " + fieldname + ", got " + p.getCurrentName());
		}
	}

	private boolean expectCurrentMaybe(JsonParser p, String fieldname) throws ExporterException, IOException {
		return fieldname.equals(p.getCurrentName());
	}
	
	private void skipStruct(JsonParser p, String fieldname) throws ExporterException, IOException {
		this.expect(p, fieldname);
		this.expect(p, JsonToken.START_OBJECT);
		while(p.nextToken() != JsonToken.END_OBJECT)
			;
	}

	private boolean skipMemberMaybe(JsonParser p, String fieldName) throws ExporterException, IOException {
		if(fieldName != null && !expectCurrentMaybe(p, fieldName))
			return false;
		expect(p, fieldName); // Advance past the field name
		JsonToken t = p.nextToken();
		if(t == JsonToken.START_ARRAY)
			skipComplex(p, 0, 1);
		else if(t == JsonToken.START_OBJECT)
			skipComplex(p, 1, 0);
		return true;
	}

	private boolean skipMember(JsonParser p, String fieldName) throws ExporterException, IOException {
		if(!skipMemberMaybe(p, fieldName))
			throw new ExporterException("Expected field name " + fieldName + ", got " + p.getCurrentName());
		return true;
	}

	private void skipComplex(JsonParser p, int structLevel, int arrayLevel) throws IOException {
		while(structLevel > 0 && arrayLevel > 0) {
			JsonToken t = p.nextToken();
			if(t == JsonToken.START_ARRAY)
				++arrayLevel;
			else if(t == JsonToken.END_ARRAY)
				--arrayLevel;
			else if(t == JsonToken.START_OBJECT)
				++structLevel;
			else if(t == JsonToken.END_OBJECT)
				--structLevel;
		}
	}
}
