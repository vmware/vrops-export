/* 
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
package net.virtualviking.vropsexport;

import java.util.regex.Matcher;

public class Config {
	public static class Field {
		private String alias;
		private String metric;
		private String prop;
		
		public Field() {
		}
	
		public Field(String alias, String name, boolean isMetric) {
			super();
			this.alias = alias;
			if(isMetric)
				this.metric = name;
			else 
				this.prop = name;
		}

		public String getAlias() {
			return alias;
		}

		public void setAlias(String alias) {
			this.alias = alias;
		}

		public String getMetric() {
			return metric;
		}
		
		public boolean hasMetric() {
			return metric != null;
		}

		public void setMetric(String metric) {
			this.metric = metric;
		}

		public String getProp() {
			return prop;
		}

		public void setProp(String prop) {
			this.prop = prop;
		}
		
		public boolean hasProp() {
			return this.prop != null;
		}
	}
	private Field[] fields;
	private String resourceType;
	private String rollupType;
	private long rollupMinutes;
	private String dateFormat;
	private String outputFormat;
	private SQLConfig sqlConfig;
	private String resourceKind;
	private String adapterKind;
	private CSVConfig csvConfig;

	public Config() {
	}

	public Field[] getFields() {
		return fields;
	}

	public void setFields(Field[] fields) {
		this.fields = fields;
	}

	public void setResourceType(String resourceType) {
		Matcher m = Patterns.adapterAndResourceKindPattern.matcher(resourceType);
		if(m.matches()) {
			this.adapterKind = m.group(1);
			this.resourceKind = m.group(2);
		} else
			this.resourceKind = resourceType;
	}

	public String getRollupType() {
		return rollupType;
	}

	public void setRollupType(String rollupType) {
		this.rollupType = rollupType;
	}

	public long getRollupMinutes() {
		return rollupMinutes;
	}

	public void setRollupMinutes(long rollup) {
		this.rollupMinutes = rollup;
	}
	
	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public String getOutputFormat() {
		return outputFormat;
	}

	public void setOutputFormat(String outputFormat) {
		this.outputFormat = outputFormat;
	}

	public SQLConfig getSqlConfig() {
		return sqlConfig;
	}

	public void setSqlConfig(SQLConfig sqlConfig) {
		this.sqlConfig = sqlConfig;
	}

	public boolean hasProps() {
		for(Field f : fields) {
			if(f.hasProp())
				return true;
		}
		return false;
	}
	
	public String getResourceKind() {
		return resourceKind;
	}

	public void setResourceKind(String resourceKind) {
		this.resourceKind = resourceKind;
	}

	public String getAdapterKind() {
		return adapterKind;
	}

	public void setAdapterKind(String adapterKind) {
		this.adapterKind = adapterKind;
	}

	public boolean hasMetrics() {
		for(Field f : fields) {
			if(f.hasMetric())
				return true;
		}
		return false;
	}
	
	public CSVConfig getCsvConfig() {
		return csvConfig;
	}

	public void setCsvConfig(CSVConfig csvConfig) {
		this.csvConfig = csvConfig;
	}
}
