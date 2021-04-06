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

import com.vmware.vropsexport.Validatable;
import com.vmware.vropsexport.exceptions.ValidationException;
import java.util.List;

public class ElasticSearchConfig implements Validatable {
  private List<String> urls;

  private String index;

  private String type;

  private int bulkSize = 10;

  private String apiKey;

  private String username;

  private String password;

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

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(final List<String> urls) {
    this.urls = urls;
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

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  @Override
  public void validate() throws ValidationException {
    if (urls == null || urls.size() == 0) {
      throw new ValidationException("ElasticSearch URL must be specified");
    }
    if (index == null) {
      throw new ValidationException("ElasticSearch index must be specified");
    }
    if (bulkSize <= 0) {
      throw new ValidationException("Bulksize must be greater than zero");
    }
  }
}
