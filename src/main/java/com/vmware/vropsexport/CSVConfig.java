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
package com.vmware.vropsexport;

@SuppressWarnings("unused")
public class CSVConfig {
  public CSVConfig() {}

  public CSVConfig(final boolean header, final String delimiter) {
    this.header = header;
    this.delimiter = delimiter;
  }

  private boolean header = true;

  private String delimiter = ",";

  public boolean isHeader() {
    return header;
  }

  public void setHeader(final boolean header) {
    this.header = header;
  }

  public String getDelimiter() {
    return delimiter;
  }

  public void setDelimiter(final String delimiter) {
    this.delimiter = delimiter;
  }
}
