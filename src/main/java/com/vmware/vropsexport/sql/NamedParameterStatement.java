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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class wraps around a {@link PreparedStatement} and allows the programmer to set parameters
 * by name instead of by index. This eliminates any confusion as to which parameter index represents
 * what. This also means that rearranging the SQL statement or adding a parameter doesn't involve
 * renumbering your indices. Code such as this:
 *
 * <p>Connection con=getConnection(); String query="select * from my_table where name=? or
 * address=?"; PreparedStatement p=con.prepareStatement(query); p.setString(1, "bob");
 * p.setString(2, "123 terrace ct"); ResultSet rs=p.executeQuery();
 *
 * <p>can be replaced with:
 *
 * <p>Connection con=getConnection(); String query="select * from my_table where name=:name or
 * address=:address"; NamedParameterStatement p=new NamedParameterStatement(con, query);
 * p.setString("name", "bob"); p.setString("address", "123 terrace ct"); ResultSet
 * rs=p.executeQuery();
 *
 * <p>Based on an example by Adam Crune
 */
@SuppressWarnings("WeakerAccess")
public class NamedParameterStatement {

  /** The statement this object is wrapping. */
  private final PreparedStatement statement;

  /** Maps parameter names to arrays of ints which are the parameter indices. */
  private final Map<String, Object> indexMap;

  /**
   * Creates a NamedParameterStatement. Wraps a call to c.{@link
   * Connection#prepareStatement(java.lang.String) prepareStatement}.
   *
   * @param connection the database connection
   * @param query the parameterized query
   * @throws SQLException if the statement could not be created
   */
  public NamedParameterStatement(final Connection connection, final String query)
      throws SQLException {
    indexMap = new HashMap<>();
    final String parsedQuery = parse(query, indexMap);
    statement = connection.prepareStatement(parsedQuery);
  }

  /**
   * Parses a query with named parameters. The parameter-index mappings are put into the map, and
   * the parsed query is returned. DO NOT CALL FROM CLIENT CODE. This method is non-private so JUnit
   * code can test it.
   *
   * @param query query to parse
   * @param paramMap map to hold parameter-index mappings
   * @return the parsed query
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  static String parse(final String query, final Map paramMap) {
    // I was originally using regular expressions, but they didn't work well for ignoring
    // parameter-like strings inside quotes.
    final int length = query.length();
    final StringBuilder parsedQuery = new StringBuilder(length);
    boolean inSingleQuote = false;
    boolean inDoubleQuote = false;
    int index = 1;

    for (int i = 0; i < length; i++) {
      char c = query.charAt(i);
      if (inSingleQuote) {
        if (c == '\'') {
          inSingleQuote = false;
        }
      } else if (inDoubleQuote) {
        if (c == '"') {
          inDoubleQuote = false;
        }
      } else {
        if (c == '\'') {
          inSingleQuote = true;
        } else if (c == '"') {
          inDoubleQuote = true;
        } else if (c == ':'
            && i + 1 < length
            && Character.isJavaIdentifierStart(query.charAt(i + 1))) {
          int j = i + 2;
          while (j < length && Character.isJavaIdentifierPart(query.charAt(j))) {
            j++;
          }
          final String name = query.substring(i + 1, j);
          c = '?'; // replace the parameter with a question mark
          i += name.length(); // skip past the end if the parameter

          List indexList = (List) paramMap.get(name);
          if (indexList == null) {
            indexList = new LinkedList();
            paramMap.put(name, indexList);
          }
          indexList.add(index);

          index++;
        }
      }
      parsedQuery.append(c);
    }

    // replace the lists of Integer objects with arrays of ints
    for (final Object o : paramMap.entrySet()) {
      final Map.Entry entry = (Map.Entry) o;
      final List list = (List) entry.getValue();
      final int[] indexes = new int[list.size()];
      int i = 0;
      for (final Object aList : list) {
        final Integer x = (Integer) aList;
        indexes[i++] = x;
      }
      entry.setValue(indexes);
    }

    return parsedQuery.toString();
  }

  public List<String> getParameterNames() {
    final ArrayList<String> result = new ArrayList<>(indexMap.size());
    for (final Object o : indexMap.keySet()) {
      result.add((String) o);
    }
    return result;
  }

  /**
   * Returns the indexes for a parameter.
   *
   * @param name parameter name
   * @return parameter indexes
   * @throws IllegalArgumentException if the parameter does not exist
   */
  private int[] getIndexes(final String name) {
    final int[] indexes = (int[]) indexMap.get(name);
    if (indexes == null) {
      throw new IllegalArgumentException("Parameter not found: " + name);
    }
    return indexes;
  }

  /**
   * Sets a parameter.
   *
   * @param name parameter name
   * @param value parameter value
   * @throws SQLException if an error occurred
   * @throws IllegalArgumentException if the parameter does not exist
   * @see PreparedStatement#setObject(int, java.lang.Object)
   */
  public void setObject(final String name, final Object value) throws SQLException {
    final int[] indexes = getIndexes(name);
    for (final int index : indexes) {
      statement.setObject(index, value);
    }
  }

  /**
   * Sets a parameter.
   *
   * @param name parameter name
   * @param value parameter value
   * @throws SQLException if an error occurred
   * @throws IllegalArgumentException if the parameter does not exist
   * @see PreparedStatement#setString(int, java.lang.String)
   */
  public void setString(final String name, final String value) throws SQLException {
    final int[] indexes = getIndexes(name);
    for (final int index : indexes) {
      statement.setString(index, value);
    }
  }

  /**
   * Sets a parameter.
   *
   * @param name parameter name
   * @param value parameter value
   * @throws SQLException if an error occurred
   * @throws IllegalArgumentException if the parameter does not exist
   * @see PreparedStatement#setInt(int, int)
   */
  public void setInt(final String name, final int value) throws SQLException {
    final int[] indexes = getIndexes(name);
    for (final int index : indexes) {
      statement.setInt(index, value);
    }
  }

  /**
   * Sets a parameter.
   *
   * @param name parameter name
   * @param value parameter value
   * @throws SQLException if an error occurred
   * @throws IllegalArgumentException if the parameter does not exist
   * @see PreparedStatement#setInt(int, int)
   */
  public void setLong(final String name, final long value) throws SQLException {
    final int[] indexes = getIndexes(name);
    for (final int index : indexes) {
      statement.setLong(index, value);
    }
  }

  /**
   * Sets a parameter.
   *
   * @param name parameter name
   * @param value parameter value
   * @throws SQLException if an error occurred
   * @throws IllegalArgumentException if the parameter does not exist
   * @see PreparedStatement#setTimestamp(int, java.sql.Timestamp)
   */
  public void setTimestamp(final String name, final Timestamp value) throws SQLException {
    final int[] indexes = getIndexes(name);
    for (final int index : indexes) {
      statement.setTimestamp(index, value);
    }
  }

  /**
   * Returns the underlying statement.
   *
   * @return the statement
   */
  public PreparedStatement getStatement() {
    return statement;
  }

  /**
   * Executes the statement.
   *
   * @return true if the first result is a {@link ResultSet}
   * @throws SQLException if an error occurred
   * @see PreparedStatement#execute()
   */
  public boolean execute() throws SQLException {
    return statement.execute();
  }

  /**
   * Executes the statement, which must be a query.
   *
   * @return the query results
   * @throws SQLException if an error occurred
   * @see PreparedStatement#executeQuery()
   */
  public ResultSet executeQuery() throws SQLException {
    return statement.executeQuery();
  }

  /**
   * Executes the statement, which must be an SQL INSERT, UPDATE or DELETE statement; or an SQL
   * statement that returns nothing, such as a DDL statement.
   *
   * @return number of rows affected
   * @throws SQLException if an error occurred
   * @see PreparedStatement#executeUpdate()
   */
  public int executeUpdate() throws SQLException {
    return statement.executeUpdate();
  }

  /**
   * Closes the statement.
   *
   * @throws SQLException if an error occurred
   * @see Statement#close()
   */
  public void close() throws SQLException {
    statement.close();
  }

  /**
   * Adds the current set of parameters as a batch entry.
   *
   * @throws SQLException if something went wrong
   */
  public void addBatch() throws SQLException {
    statement.addBatch();
  }

  /**
   * Executes all of the batched statements.
   *
   * <p>See {@link Statement#executeBatch()} for details.
   *
   * @return update counts for each statement
   * @throws SQLException if something went wrong
   */
  public int[] executeBatch() throws SQLException {
    return statement.executeBatch();
  }
}
