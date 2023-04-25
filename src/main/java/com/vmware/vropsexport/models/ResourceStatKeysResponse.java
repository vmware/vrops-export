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
package com.vmware.vropsexport.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

public class ResourceStatKeysResponse {
  @JsonAlias({"stat-key"})
  private List<Map<String, String>> statKeys;

  public List<Map<String, String>> getStatKeys() {
    return statKeys;
  }

  public void setStatKeys(final List<Map<String, String>> statKeys) {
    this.statKeys = statKeys;
  }
}
