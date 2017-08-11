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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RowMetadata {
	private final String resourceKind;
	
	private final String adapterKind;
	
	private final Map<String, Integer> metricMap = new HashMap<>();
	
	private final Map<String, Integer> propMap = new HashMap<>();
	
	private final Map<String, Integer> metricAliasMap = new HashMap<>();
	
	private final Map<String, Integer> propAliasMap = new HashMap<>();
	
	private int[] propInsertionPoints;
	
	private Pattern parentPattern = Pattern.compile("^\\$parent\\:([_A-Za-z][_A-Za-z0-9]*)\\.(.+)$");
	
	public RowMetadata(Config conf) throws ExporterException {
		this.resourceKind = conf.getResourceKind();
		this.adapterKind = conf.getAdapterKind();
		int mp = 0;
		int pp = 0;
		List<Integer> pip = new ArrayList<>(); 
		for(Config.Field fld : conf.getFields()) {
			if(fld.hasMetric()) {
				String metricKey = fld.getMetric();
				if(metricMap.containsKey(metricKey)) 
					throw new ExporterException("Repeated metrics are not supported. Offending metric: " + metricKey);
				metricMap.put(metricKey, mp);
				metricAliasMap.put(fld.getAlias(), mp++);
			} else {
				if(fld.hasProp()) {
					String propKey = fld.getProp();
					if(metricMap.containsKey(propKey)) 
						throw new ExporterException("Repeated properties are not supported. Offending property: " + propKey);
					propMap.put(fld.getProp(), pp);
					propAliasMap.put(fld.getAlias(), pp++);
					pip.add(mp);
				}
			}
		}
		propInsertionPoints = new int[pip.size()];
		for(int i = 0; i < pip.size(); ++i) 
			propInsertionPoints[i] = pip.get(i);
	}
	
	private RowMetadata(RowMetadata child) throws ExporterException {
		this.propInsertionPoints = child.propInsertionPoints;
		String t = null;
		for(Map.Entry<String, Integer> e : child.propMap.entrySet()) {
			String p = e.getKey();
			Matcher m = parentPattern.matcher(p);
			if(m.matches())  {
				if(t == null)
					t = m.group(1);
				else if(!t.equals(m.group(1))) {
					throw new ExporterException("Only one parent type is currently supported");
				}
				propMap.put(m.group(2), e.getValue());
			} else {
				propMap.put("_placeholder_" + p, e.getValue());
			}
		}
		for(Map.Entry<String, Integer> e : child.metricMap.entrySet()) {
			String mt = e.getKey();
			Matcher m = parentPattern.matcher(mt);
			if(m.matches()) {
				if(t == null)
					t = m.group(1);
				else if(!t.equals(m.group(1))) {
					throw new ExporterException("Only one parent type is currently supported");
				}
				metricMap.put(m.group(2), e.getValue());
			} else {
				metricMap.put("_placholder_" + mt, e.getValue());
			}
		}
		this.resourceKind = t;
		this.adapterKind = null; // TODO: It should be possible to specify adapter type as well!
	}
	
	public RowMetadata forParent() throws ExporterException {
		return new RowMetadata(this);
	}

	public Map<String, Integer> getMetricMap() {
		return metricMap;
	}

	public Map<String, Integer> getPropMap() {
		return propMap;
	}

	public int[] getPropInsertionPoints() {
		return propInsertionPoints;
	}
	
	public int getMetricIndex(String metric) {
		return metricMap.containsKey(metric) ? metricMap.get(metric) : -1;
	}
	
	public int getPropertyIndex(String property) {
		return propMap.containsKey(property) ? propMap.get(property) : -1;
	}
	
	public int getMetricIndexByAlias(String metric) {
		return metricAliasMap.containsKey(metric) ? metricAliasMap.get(metric) : -1;
	}
	
	public int getPropertyIndexByAlias(String property) {
		return propAliasMap.containsKey(property) ? propAliasMap.get(property) : -1;
	}
	
	public Row newRow(long timestamp) {
		return new Row(timestamp, metricMap.size(), propMap.size());
	}
	
	public String getResourceKind() {
		return resourceKind;
	}

	public String getAdapterKind() {
		return adapterKind;
	}

	public boolean hasProperties() {
		return propMap.size() > 0;
	}

	public boolean needsPropertyLoad() {
		if(!hasProperties())
			return false;
		for(String key : propMap.keySet()) {
			if(!(key.equals("$resId") || key.equals("$resName")))
				return true;
		}
		return false;
	}
	
	public boolean isValid() {
		return resourceKind != null;
	}
}
