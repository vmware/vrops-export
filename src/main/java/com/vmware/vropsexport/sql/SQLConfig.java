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
package com.vmware.vropsexport.sql;

import com.vmware.vropsexport.Validatable;
import com.vmware.vropsexport.exceptions.ValidationException;

public class SQLConfig implements Validatable {
  private String connectionString;

  private String username;

  private String password;

  private String databaseType;

  private String driver;

  private String sql;

  private int batchSize;

  public SQLConfig() {}

  public String getConnectionString() {
    return connectionString;
  }

  public void setConnectionString(final String connectionString) {
    this.connectionString = connectionString;
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

  public String getDriver() {
    return driver;
  }

  public void setDriver(final String driver) {
    this.driver = driver;
  }

  public String getDatabaseType() {
    return databaseType;
  }

  public void setDatabaseType(final String databaseType) {
    this.databaseType = databaseType;
  }

  public String getSql() {
    return sql;
  }

  public void setSql(final String sql) {
    this.sql = sql;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(final int batchSize) {
    this.batchSize = batchSize;
  }

  @Override
  public void validate() throws ValidationException {
    if (sql == null) {
      throw new ValidationException("'sql' must be specified");
    }
    if (connectionString == null) {
      throw new ValidationException("'connectionString' must be specified");
    }
    if (databaseType == null && driver == null) {
      throw new ValidationException("'databaseType' or 'driver' must be specified");
    }
    if (databaseType != null && driver != null) {
      throw new ValidationException("'databaseType' and 'driver' are mutually exclusive");
    }
  }
}
