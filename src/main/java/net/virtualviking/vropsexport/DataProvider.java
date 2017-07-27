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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.json.JSONException;
import org.json.JSONObject;

public interface DataProvider {
	public Map<String, String> fetchProps(String id) throws IOException, HttpException;
	
	public JSONObject getParentOf(String id, String parentType) throws JSONException, IOException, HttpException;
	
	public InputStream fetchMetricStream(List<JSONObject> resList, RowMetadata meta, long begin, long end) throws IOException, HttpException;
	
	public String getResourceName(String resourceId) throws JSONException, IOException, HttpException;
}
