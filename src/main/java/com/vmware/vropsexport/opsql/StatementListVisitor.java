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

package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.utils.ParseUtils;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;

public class StatementListVisitor extends OpsqlBaseVisitor<Object> {
  private final List<RunnableStatement> statements = new ArrayList<>();
  private final SessionContext sessionContext;

  public StatementListVisitor(final SessionContext sessionContext) {
    this.sessionContext = sessionContext;
  }

  @Override
  public Object visitTimeZoneStatement(final OpsqlParser.TimeZoneStatementContext ctx) {
    try {
      final ZoneId tz = ZoneId.of(ParseUtils.unquote(ctx.StringLiteral().getText()));
      statements.add((sessionContext) -> sessionContext.setTimezone(tz));
      return null;
    } catch (final ZoneRulesException e) {
      throw new OpsqlException(e.getMessage());
    }
  }

  @Override
  public Object visitFormatStatement(final OpsqlParser.FormatStatementContext ctx) {
    return super.visitFormatStatement(ctx);
  }

  @Override
  public Object visitQueryStatement(final OpsqlParser.QueryStatementContext ctx) {
    final QueryBuilderVisitor v = new QueryBuilderVisitor(sessionContext);
    v.visitQueryStatement(ctx);
    statements.add(v.getQuery());
    return null;
  }

  public List<RunnableStatement> getStatements() {
    return statements;
  }
}
