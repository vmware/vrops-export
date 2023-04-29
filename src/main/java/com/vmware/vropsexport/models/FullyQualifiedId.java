/*
 *
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
 *
 */

package com.vmware.vropsexport.models;

import java.util.Objects;

public class FullyQualifiedId {
  private String adapterKind;
  private final String resourceKind;
  private final String id;

  public FullyQualifiedId(final String adapterKind, final String resourceKind, final String id) {
    this.adapterKind = adapterKind;
    this.resourceKind = resourceKind;
    this.id = id;
  }

  public String getAdapterKind() {
    return adapterKind;
  }

  public void setAdapterKind(final String adapterKind) {
    this.adapterKind = adapterKind;
  }

  public String getResourceKind() {
    return resourceKind;
  }

  public String getId() {
    return id;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final FullyQualifiedId that = (FullyQualifiedId) o;
    return Objects.equals(adapterKind, that.adapterKind)
        && Objects.equals(resourceKind, that.resourceKind)
        && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(adapterKind, resourceKind, id);
  }

  @Override
  public String toString() {
    return "FullyQualifiedId{"
        + "adapterKind='"
        + adapterKind
        + '\''
        + ", resourceKind='"
        + resourceKind
        + '\''
        + ", id='"
        + id
        + '\''
        + '}';
  }
}
