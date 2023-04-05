package com.vmware.vropsexport.opqsl;

import com.vmware.vropsexport.opsql.NegationRemover;
import com.vmware.vropsexport.opsql.QueryAST;
import org.junit.Assert;
import org.junit.Test;

public class NegationRemoverTest {
  private static final QueryAST.Expression simple =
      new QueryAST.Not(
          new QueryAST.And(
              new QueryAST.Comparison(
                  new QueryAST.Identifier("a"), new QueryAST.StringLiteral("a"), "EQ"),
              new QueryAST.Comparison(
                  new QueryAST.Identifier("b"), new QueryAST.StringLiteral("b"), "NE")));

  private static final QueryAST.Expression simpleResult =
      new QueryAST.Or(
          new QueryAST.Comparison(
              new QueryAST.Identifier("a"), new QueryAST.StringLiteral("a"), "NE"),
          new QueryAST.Comparison(
              new QueryAST.Identifier("b"), new QueryAST.StringLiteral("b"), "EQ"));

  private static final QueryAST.Expression complex =
      new QueryAST.Not(
          new QueryAST.And(
              new QueryAST.Not(
                  new QueryAST.Or(
                      new QueryAST.Comparison(
                          new QueryAST.Identifier("x"), new QueryAST.DoubleLiteral(42), "LT"),
                      new QueryAST.Comparison(
                          new QueryAST.Identifier("z"), new QueryAST.DoubleLiteral(17), "GT"))),
              new QueryAST.Comparison(
                  new QueryAST.Identifier("a"), new QueryAST.StringLiteral("a"), "EQ")));

  private static final QueryAST.Expression complexResult =
      new QueryAST.Or(
          new QueryAST.And(
              new QueryAST.Comparison(
                  new QueryAST.Identifier("x"), new QueryAST.DoubleLiteral(42), "GT_EQ"),
              new QueryAST.Comparison(
                  new QueryAST.Identifier("z"), new QueryAST.DoubleLiteral(17), "LT_EQ")),
          new QueryAST.Comparison(
              new QueryAST.Identifier("a"), new QueryAST.StringLiteral("a"), "NE"));

  @Test
  public void testSimple() {
    runTest(simple, simpleResult);
  }

  @Test
  public void testComplex() {
    runTest(complex, complexResult);
  }

  private void runTest(QueryAST.Expression input, QueryAST.Expression wanted) {
    QueryAST.Expression transformed = NegationRemover.removeNegations(input);
    Assert.assertNotNull(transformed);
    System.out.println("Transformed " + input + " ==> " + transformed);
    Assert.assertEquals(wanted, transformed);
  }
}
