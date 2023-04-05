package com.vmware.vropsexport.opsql;

import java.util.Stack;

public class QueryListener extends OpsqlBaseListener {
  private String resourceKind;

  private String setAdapterKind;

  private QueryAST.Expression filter;

  private final Stack<QueryAST.Expression> wipStack = new Stack<>();

  private final Stack<QueryAST.Expression> finishedStack = new Stack();

  public QueryListener() {}

  private String[] splitResourceSpecifier(String rs) {
    int p = rs.indexOf(":");
    return p == -1
        ? new String[] {null, rs}
        : new String[] {rs.substring(0, p), rs.substring(p + 1)};
  }

  @Override
  public void exitResourceSpecifier(OpsqlParser.ResourceSpecifierContext ctx) {
    String[] parts = splitResourceSpecifier(ctx.Identifier().getText());
    setAdapterKind = parts[0];
    resourceKind = parts[1];
  }

  @Override
  public void enterAndExpression(OpsqlParser.AndExpressionContext ctx) {
    wipStack.push(new QueryAST.And());
  }

  @Override
  public void exitAndExpression(OpsqlParser.AndExpressionContext ctx) {
    QueryAST.And current = (QueryAST.And) wipStack.pop();
    current.setRight(finishedStack.pop());
    current.setLeft(finishedStack.pop());
    finishedStack.push(current);
  }

  @Override
  public void enterOrExpression(OpsqlParser.OrExpressionContext ctx) {
    wipStack.push(new QueryAST.Or());
  }

  @Override
  public void exitOrExpression(OpsqlParser.OrExpressionContext ctx) {
    QueryAST.Or current = (QueryAST.Or) wipStack.pop();
    current.setRight(finishedStack.pop());
    current.setLeft(finishedStack.pop());
    finishedStack.push(current);
  }
}
