package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;

public class ASTCreatorVisitor extends OpsqlBaseVisitor<QueryAST.Expression> {
  private static final Map<String, String> toInternalOp = new HashMap<>();

  static {
    toInternalOp.put("=", "EQ");
    toInternalOp.put("!=", "NE");
    toInternalOp.put(">", "GT");
    toInternalOp.put(">=", "GT_EQ");
    toInternalOp.put("<", "LT");
    toInternalOp.put("<=", "LT_EQ");
  }

  @Override
  public QueryAST.Expression visitAndExpression(OpsqlParser.AndExpressionContext ctx) {
    return new QueryAST.And(ctx.getChild(0).accept(this), ctx.getChild(2).accept(this));
  }

  @Override
  public QueryAST.Expression visitOrExpression(OpsqlParser.OrExpressionContext ctx) {
    return new QueryAST.Or(ctx.getChild(0).accept(this), ctx.getChild(2).accept(this));
  }

  @Override
  public QueryAST.Expression visitNegation(OpsqlParser.NegationContext ctx) {
    return new QueryAST.Not(ctx.getChild(1).accept(this));
  }

  @Override
  public QueryAST.Expression visitComparison(OpsqlParser.ComparisonContext ctx) {
    return new QueryAST.Comparison(
        ctx.getChild(0).accept(this), ctx.getChild(2).accept(this), toInternalOp.get(ctx.getChild(1).getText()));
  }

  @Override
  public QueryAST.Expression visitSpecialTerm(OpsqlParser.SpecialTermContext ctx) {
    // TODO: This should have its own node
    return new QueryAST.Identifier(ctx.getText());
  }

  @Override
  public QueryAST.Expression visitIdentifierTerm(OpsqlParser.IdentifierTermContext ctx) {
    return new QueryAST.Identifier(ctx.getText());
  }

  @Override
  public QueryAST.Expression visitNumber(OpsqlParser.NumberContext ctx) {
    return new QueryAST.DoubleLiteral(Double.parseDouble(ctx.getText()));
  }

  @Override
  public QueryAST.Expression visitStringLiteral(OpsqlParser.StringLiteralContext ctx) {
    return new QueryAST.StringLiteral(ctx.getText());
  }
}
