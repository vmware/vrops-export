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
package net.virtualviking.vropsexport;

import java.util.List;
import java.util.TreeMap;

public class Rowset {	
	private final String resourceId;
	
	private final TreeMap<Long, Row> rows;

	public Rowset(String resourceId, TreeMap<Long, Row> rows) {
		super();
		this.resourceId = resourceId;
		this.rows = rows;
	}

	public String getResourceId() {
		return resourceId;
	}

	public TreeMap<Long, Row> getRows() {
		return rows;
	}
}
