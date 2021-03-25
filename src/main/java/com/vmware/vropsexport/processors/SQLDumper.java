/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport.processors;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.DataProvider;
import com.vmware.vropsexport.ExporterException;
import com.vmware.vropsexport.Row;
import com.vmware.vropsexport.RowMetadata;
import com.vmware.vropsexport.Rowset;
import com.vmware.vropsexport.RowsetProcessor;
import com.vmware.vropsexport.RowsetProcessorFacotry;
import com.vmware.vropsexport.sql.NamedParameterStatement;
import com.vmware.vropsexport.sql.SQLConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.http.HttpException;

@SuppressWarnings("WeakerAccess")
public class SQLDumper implements RowsetProcessor {
    private static final int DEFAULT_BATCH_SIZE = 1000;

    private static final Map<String, String> drivers = new HashMap<>();

    private final int batchSize;

    static {
        drivers.put("postgres", "org.postgresql.Driver");
        drivers.put("mssql", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        drivers.put("mysql", "com.mysql.jdbc.Driver");
        drivers.put("oracle", "oracle.jdbc.driver.OracleDriver");
    }

    public static class Factory implements RowsetProcessorFacotry {
        private BasicDataSource ds;

        @Override
        public synchronized RowsetProcessor makeFromConfig(final OutputStream out, final Config config, final DataProvider dp)
                throws ExporterException {
            final SQLConfig sqlc = config.getSqlConfig();
            if (sqlc == null) {
                throw new ExporterException("SQL section must be present in the definition file");
            }

            // Determine batchsize
            //
            int batchSize = sqlc.getBatchSize();
            if (batchSize == 0) {
                batchSize = DEFAULT_BATCH_SIZE;
            }

            // The driver can be either read directly or derived from the database type
            //
            String driver = sqlc.getDriver();
            if (driver == null) {
                final String dbType = sqlc.getDatabaseType();
                if (dbType == null) {
                    throw new ExporterException("Database type or driver name must be specified");
                }
                driver = drivers.get(dbType);
                if (driver == null) {
                    throw new ExporterException("Database type " + dbType + " is not recognized. Check spelling or try to specifying the driver class instead!");
                }

                // Make sure we can load the driver
                //
                try {
                    Class.forName(driver);
                } catch (final ClassNotFoundException e) {
                    throw new ExporterException("Could not load JDBC driver " + driver + ". Make sure you have set the JDBC_JAR env variable correctly");
                }
            }
            if (ds == null) {
                ds = new BasicDataSource();
                if (sqlc.getConnectionString() == null) {
                    throw new ExporterException("SQL connection URL must be specified");
                }

                // Use either database type or driver.
                //
                ds.setDefaultAutoCommit(false);
                ds.setDriverClassName(driver);
                ds.setUrl(sqlc.getConnectionString());
                if (sqlc.getUsername() != null) {
                    ds.setUsername(sqlc.getUsername());
                }
                if (sqlc.getPassword() != null) {
                    ds.setPassword(sqlc.getPassword());
                }
            }
            if (sqlc.getSql() == null) {
                throw new ExporterException("SQL statement must be specified");
            }
            return new SQLDumper(ds, dp, sqlc.getSql(), batchSize);
        }

        @Override
        public boolean isProducingOutput() {
            return false;
        }
    }

    private final DataSource ds;

    private final DataProvider dp;

    private final String sql;

    public SQLDumper(final DataSource ds, final DataProvider dp, final String sql, final int batchSize) {
        super();
        this.ds = ds;
        this.dp = dp;
        this.sql = sql;
        this.batchSize = batchSize;
    }

    @Override
    public void preamble(final RowMetadata meta, final Config conf) throws ExporterException {
        // Nothing to do...
    }

    @Override
    public void process(final Rowset rowset, final RowMetadata meta) throws ExporterException {
        try {
            NamedParameterStatement stmt = null;
            final Connection conn = ds.getConnection();
            try {
                stmt = new NamedParameterStatement(conn, sql);
                int rowsInBatch = 0;
                for (final Row row : rowset.getRows().values()) {
                    for (final String fld : stmt.getParameterNames()) {
                        // Deal with special cases
                        //
                        if ("timestamp".equals(fld)) {
                            stmt.setObject("timestamp", new java.sql.Timestamp(row.getTimestamp()));
                        } else if ("resName".equals(fld)) {
                            stmt.setString("resName", dp.getResourceName(rowset.getResourceId()));
                        } else {
                            // Does the name refer to a metric?
                            //
                            int p = meta.getMetricIndexByAlias(fld);
                            if (p != -1) {
                                stmt.setObject(fld, row.getMetric(p));
                            } else {
                                // Not a metric, so it must be a property then.
                                //
                                p = meta.getPropertyIndexByAlias(fld);
                                if (p == -1) {
                                    throw new ExporterException("Field " + fld + " is not defined");
                                }
                                stmt.setString(fld, row.getProp(p));
                            }
                        }
                    }
                    stmt.addBatch();
                    if (++rowsInBatch > batchSize) {
                        stmt.executeBatch();
                        rowsInBatch = 0;
                    }
                }
                // Push dangling batch
                //
                if (rowsInBatch > 0 && stmt != null) {
                    stmt.executeBatch();
                }
                conn.commit();
            } finally {
                if (stmt != null) {
                    stmt.close();
                }
                conn.close();
            }
        } catch (final SQLException | HttpException | IOException e) {
            throw new ExporterException(e);
        }
    }

    @Override
    public void close() throws ExporterException {
        // Nothing to do
    }
}
