package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.*;

public class QueryCompiler {
  private static class ExceptionThrowerListener extends BaseErrorListener {
    @Override
    public void syntaxError(
        final Recognizer<?, ?> recognizer,
        final Object offendingSymbol,
        final int line,
        final int charPositionInLine,
        final String msg,
        final RecognitionException e) {
      throw e;
    }
  }

  public static Query compile(final String qtext) throws RecognitionException {
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(qtext));
    final OpsqlParser parser = new OpsqlParser(new CommonTokenStream(lexer));
    parser.addErrorListener(new ExceptionThrowerListener());
    final OpsqlParser.QueryContext q = parser.query();

    final QueryBuilderVisitor queryBuilder = new QueryBuilderVisitor();
    q.accept(queryBuilder);

    return queryBuilder.getQuery();
  }
}
