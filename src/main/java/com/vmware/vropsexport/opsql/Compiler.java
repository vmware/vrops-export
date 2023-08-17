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
package com.vmware.vropsexport.opsql;

import java.util.List;
import org.antlr.v4.runtime.*;

public class Compiler {
  private static class ExceptionThrowerListener extends BaseErrorListener {
    public static ExceptionThrowerListener instance = new ExceptionThrowerListener();

    @Override
    public void syntaxError(
        final Recognizer<?, ?> recognizer,
        final Object offendingSymbol,
        final int line,
        final int charPositionInLine,
        final String msg,
        final RecognitionException e) {
      throw new OpsqlException(msg, e);
    }
  }

  public static List<RunnableStatement> compile(final String qtext) throws RecognitionException {
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(qtext));
    lexer.removeErrorListeners();
    lexer.addErrorListener(ExceptionThrowerListener.instance);
    final OpsqlParser parser = new OpsqlParser(new CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(ExceptionThrowerListener.instance);
    final OpsqlParser.StatementListContext q = parser.statementList();
    final StatementListVisitor builder = new StatementListVisitor();
    q.accept(builder);
    return builder.getStatements();
  }
}
