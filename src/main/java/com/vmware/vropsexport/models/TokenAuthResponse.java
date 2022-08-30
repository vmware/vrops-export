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

public class TokenAuthResponse {
  private String id_token;

  private String expires_in;

  private String scope;

  private String access_token;

  private String refresh_token;

  public String getId_token() {
    return id_token;
  }

  public void setId_token(final String id_token) {
    this.id_token = id_token;
  }

  public String getExpires_in() {
    return expires_in;
  }

  public void setExpires_in(final String expires_in) {
    this.expires_in = expires_in;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

  public String getAccess_token() {
    return access_token;
  }

  public void setAccess_token(final String access_token) {
    this.access_token = access_token;
  }

  public String getRefresh_token() {
    return refresh_token;
  }

  public void setRefresh_token(final String refresh_token) {
    this.refresh_token = refresh_token;
  }
}
