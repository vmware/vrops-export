/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport.elasticsearch;

public class ElasticSearchConfig {
  private String url;

  private String index;

  private String type;

  private int bulkSize = 10;

  private String apiKey;

  public int getBulkSize() {
    return bulkSize;
  }

  public void setBulkSize(final int bulkSize) {
    this.bulkSize = bulkSize;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(final String apiKey) {
    this.apiKey = apiKey;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(final String index) {
    this.index = index;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }
}
