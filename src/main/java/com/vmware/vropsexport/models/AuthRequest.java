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

public class AuthRequest {
  private final String authSource;
  private final String username;
  private final String password;

  public AuthRequest(final String authSource, final String username, final String password) {
    this.authSource = authSource;
    this.username = username;
    this.password = password;
  }

  public String getAuthSource() {
    return authSource;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }
}
