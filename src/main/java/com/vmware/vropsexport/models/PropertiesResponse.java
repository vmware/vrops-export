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
package com.vmware.vropsexport.models;

public class PropertiesResponse {
  public static class Property {
    String name;

    String value;

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    public String getValue() {
      return value;
    }

    public void setValue(final String value) {
      this.value = value;
    }
  }

  private Property[] property;

  public Property[] getProperty() {
    return property;
  }

  public void setProperty(final Property[] property) {
    this.property = property;
  }
}
