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

import java.util.BitSet;
import java.util.NoSuchElementException;

public class Row {
	public int FIRST_METRIC_OFFSET = 2;

	private final long timestamp;
	
	private final BitSet definedMetrics;
		
	private final double[] metrics;
		
	private final String[] props;

	public Row(long timestamp, int nMetrics, int nProps) {
		super();
		this.timestamp = timestamp;
		this.metrics = new double[nMetrics];
		this.props = new String[nProps];
		this.definedMetrics = new BitSet(nMetrics);
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Double getMetric(int i) {
		return definedMetrics.get(i) ? metrics[i] : null;
	}
	
	public String getProp(int i) {
		return props[i];
	}
	
	public void setMetric(int i, double m) {
		metrics[i] = m;
		definedMetrics.set(i);
	}
	
	public void setProp(int i, String prop) {
		props[i] = prop;
	}
	
	public java.util.Iterator<Object> iterator(RowMetadata meta) {
		return new Iterator(meta);
	}
	
	public int getNumProps() {
		return props.length;
	}
	
	public int getNumMetrics() {
		return metrics.length;
	}
	
	private class Iterator implements java.util.Iterator<Object> {
		private int mc;
		
		private int pc;
				
		private RowMetadata meta;
		
		public Iterator(RowMetadata meta) {
			this.meta = meta;
		}

		@Override
		public boolean hasNext() {
			return pc < props.length || mc < metrics.length;
		}

		@Override
		public Object next() {
			if(!this.hasNext()) 
				throw new NoSuchElementException();
			if(pc < props.length && meta.getPropInsertionPoints()[pc] == mc)
				return getProp(pc++);
			return getMetric(mc++);
		}
	}

	public Object[] flatten(RowMetadata meta) {
		Object[] answer = new Object[FIRST_METRIC_OFFSET + metrics.length + props.length];
		int i = 0;
		answer[0] = timestamp;
		i = FIRST_METRIC_OFFSET;
		for(java.util.Iterator<Object> itor = this.iterator(meta); itor.hasNext();)
			answer[i++] = itor.next();
		return answer;
	}

	public void merge(Row r) {
		// Merge metrics
		//
		for(int i = 0; i < r.getNumMetrics(); ++i) {
			Double d = r.getMetric(i);
			if(d != null)
				metrics[i] = d;
		}

		// Merge properties
		//
		for(int i = 0; i < r.getNumProps(); ++i) {
			String p = r.getProp(i);
			if(p != null)
				props[i] = p;
		}
	}
}
