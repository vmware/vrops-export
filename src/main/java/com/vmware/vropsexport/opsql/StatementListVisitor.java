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

import com.vmware.vropsexport.utils.BeanTools;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class StatementListVisitor extends BaseVisitor {
  private final List<RunnableStatement> statements = new ArrayList<>();

  public StatementListVisitor() {}

  @Override
  public Object visitSetStatement(final OpsqlParser.SetStatementContext ctx) {
    final String key = ctx.Identifier().getText();
    final Object value = ctx.propertyLiteral().accept(this);
    statements.add(
        sessionContext -> {
          try {
            BeanTools.set(sessionContext.getConfig(), key, value);
          } catch (final InvocationTargetException e) {
            throw new OpsqlException(e.getTargetException().getMessage());
          } catch (final IllegalAccessException | NoSuchFieldException | NoSuchMethodException e) {
            throw new OpsqlException("No such field: " + key);
          }
        });
    return super.visitSetStatement(ctx);
  }

  @Override
  public Object visitQueryStatement(final OpsqlParser.QueryStatementContext ctx) {
    final QueryBuilderVisitor v = new QueryBuilderVisitor();
    v.visitQueryStatement(ctx);
    statements.add(v.getQuery());
    return null;
  }

  public List<RunnableStatement> getStatements() {
    return statements;
  }
}
