package com.vmware.vropsexport.opsql;

public class QueryListener extends OpsqlBaseListener {
  private String resourceKind;

  private String setAdapterKind;

  private QueryAST.Expression filter;

  public QueryListener() {
  }

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
  public void exitFilterExpression(OpsqlParser.FilterExpressionContext ctx) {
    super.exitFilterExpression(ctx);
  }
}
