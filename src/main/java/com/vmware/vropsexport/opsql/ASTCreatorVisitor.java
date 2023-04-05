package com.vmware.vropsexport.opsql;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.Map;

public class ASTCreatorVisitor extends OpsqlBaseVisitor<QueryAST.Expression> {

  private interface Resolver {
    void resolve(QueryAST.Expression expr);
  }

  Map<ParseTree, Resolver> resolvers = new HashMap<>();

  private void resolve(ParseTree t, QueryAST.Expression e) {
    Resolver r = resolvers.remove(t);
    if (r != null) {
      r.resolve(e);
    }
  }

  private void deferBinaryExpression(ParseTree ctx, QueryAST.BinaryExpression here) {
    resolvers.put(ctx.getChild(0), here::setLeft);
    resolvers.put(ctx.getChild(2), here::setRight);
  }

  @Override
  public QueryAST.Expression visitAndExpression(OpsqlParser.AndExpressionContext ctx) {
    QueryAST.And here = new QueryAST.And();
    deferBinaryExpression(ctx, here);
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitOrExpression(OpsqlParser.OrExpressionContext ctx) {
    QueryAST.Or here = new QueryAST.Or();
    deferBinaryExpression(ctx, here);
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitNegation(OpsqlParser.NegationContext ctx) {
    QueryAST.Not here = new QueryAST.Not();
    resolvers.put(ctx.getChild(0), here::setChild);
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitComparison(OpsqlParser.ComparisonContext ctx) {
    QueryAST.Comparison here =
        new QueryAST.Comparison(ctx.getChild(1).getText()); // TODO: Translate relational operator
    deferBinaryExpression(ctx, here);
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitSpecialTerm(OpsqlParser.SpecialTermContext ctx) {
    // TODO: This should have its own node
    QueryAST.Identifier here = new QueryAST.Identifier(ctx.getText());
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitIdentifierTerm(OpsqlParser.IdentifierTermContext ctx) {
    QueryAST.Identifier here = new QueryAST.Identifier(ctx.getText());
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitNumber(OpsqlParser.NumberContext ctx) {
    QueryAST.DoubleLiteral here = new QueryAST.DoubleLiteral(Double.parseDouble(ctx.getText()));
    resolve(ctx, here);
    return here;
  }

  @Override
  public QueryAST.Expression visitStringLiteral(OpsqlParser.StringLiteralContext ctx) {
    QueryAST.StringLiteral here = new QueryAST.StringLiteral(ctx.getText());
    resolve(ctx, here);
  }
}
