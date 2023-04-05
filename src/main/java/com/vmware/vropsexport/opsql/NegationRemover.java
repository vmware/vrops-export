package com.vmware.vropsexport.opsql;

public class NegationRemover implements QueryAST.Visitor {
  @Override
  public void onVisit(QueryAST.Expression node) {
    if (!(node instanceof QueryAST.Not)) {
      return;
    }
    node.getParent().replaceChild(node, node.negate());
  }

  public static QueryAST.Expression removeNegations(QueryAST.Expression node) {
    while (node instanceof QueryAST.Not) {
      node = node.negate();
    }
    node.visit(new NegationRemover());
    return node;
  }
}
