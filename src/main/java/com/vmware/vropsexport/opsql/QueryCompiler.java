package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class QueryCompiler {
  public Query compile(final String qtext) {
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(qtext));
    final OpsqlParser parser = new OpsqlParser(new CommonTokenStream(lexer));
    final OpsqlParser.QueryContext q = parser.query();
    if (q.exception != null) {
      throw q.exception;
    }

    final QueryBuilderVisitor queryBuilder = new QueryBuilderVisitor();
    q.accept(queryBuilder);

    return queryBuilder.getQuery();
  }
}
