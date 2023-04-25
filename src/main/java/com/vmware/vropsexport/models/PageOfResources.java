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

import java.util.List;

public class PageOfResources {
  private PageInfo pageInfo;

  private List<NamedResource> resourceList;

  public PageInfo getPageInfo() {
    return pageInfo;
  }

  public void setPageInfo(final PageInfo pageInfo) {
    this.pageInfo = pageInfo;
  }

  public List<NamedResource> getResourceList() {
    return resourceList;
  }

  public void setResourceList(final List<NamedResource> resourceList) {
    this.resourceList = resourceList;
  }
}
