/*
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
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

import com.vmware.vropsexport.models.NamedResource;
import org.apache.http.HttpException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface DataProvider {
  Map<String, String> fetchProps(String id) throws IOException, HttpException;

  NamedResource getParentOf(String id, String parentType) throws IOException, HttpException;

  InputStream fetchMetricStream(NamedResource[] resList, RowMetadata meta, long begin, long end)
      throws IOException, HttpException;

  String getResourceName(String resourceId) throws IOException, HttpException;

  List<String> getStatKeysForResource(final String resourceId) throws IOException, HttpException;
}
