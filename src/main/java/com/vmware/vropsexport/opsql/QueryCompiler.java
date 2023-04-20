package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.*;

public class QueryCompiler {
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

  public static Query compile(final String qtext) throws RecognitionException {
    final OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(qtext));
    lexer.addErrorListener(ExceptionThrowerListener.instance);
    final OpsqlParser parser = new OpsqlParser(new CommonTokenStream(lexer));
    parser.addErrorListener(ExceptionThrowerListener.instance);
    final OpsqlParser.QueryContext q = parser.query();

    final QueryBuilderVisitor queryBuilder = new QueryBuilderVisitor();
    q.accept(queryBuilder);

    return queryBuilder.getQuery();
  }
}
