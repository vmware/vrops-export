package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class QueryCompiler {
  public Query compile(String qtext) {
    OpsqlLexer lexer = new OpsqlLexer(CharStreams.fromString(qtext));
    OpsqlParser parser = new OpsqlParser(new CommonTokenStream(lexer));
    QueryListener ql = new QueryListener();
    // parser.addParseListener(ql);
    OpsqlParser.QueryContext q = parser.query();
    // return ql.getQuery();

    ASTCreatorVisitor acv = new ASTCreatorVisitor();
    QueryAST.Expression filterExp =
        q.filterExpression() != null ? q.filterExpression().accept(acv) : null;

    return new Query(); // TODO: Temporary
  }
}
