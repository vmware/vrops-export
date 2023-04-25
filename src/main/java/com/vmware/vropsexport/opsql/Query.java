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
package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.models.ResourceRequest;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Query {
  final ResourceRequest resourceRequest;

  final List<Config.Field> fields;

  public Query() {
    resourceRequest = new ResourceRequest();
    fields = new LinkedList<>();
  }

  public Query(final ResourceRequest resourceRequest, final List<Config.Field> fields) {
    this.resourceRequest = resourceRequest;
    this.fields = fields;
  }

  public ResourceRequest getResourceRequest() {
    return resourceRequest;
  }

  public List<Config.Field> getFields() {
    return fields;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query query = (Query) o;
    return Objects.equals(resourceRequest, query.resourceRequest)
        && Objects.equals(fields, query.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceRequest, fields);
  }

  public Config toConfig() {
    final Config conf = new Config();
    conf.setQuery(resourceRequest);
    conf.setFields(fields);
    return conf;
  }
}
