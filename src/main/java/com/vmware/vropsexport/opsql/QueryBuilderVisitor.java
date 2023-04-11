package com.vmware.vropsexport.opsql;

import com.sun.tools.javac.util.List;
import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.models.ResourceRequest;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class QueryBuilderVisitor extends OpsqlBaseVisitor {
  private enum Mode {
    metric,
    property,
    reserved
  }

  private static final Map<String, String> toInternalOp = new HashMap<>();

  static {
    toInternalOp.put("=", "EQ");
    toInternalOp.put("!=", "NE");
    toInternalOp.put(">", "GT");
    toInternalOp.put(">=", "GT_EQ");
    toInternalOp.put("<", "LT");
    toInternalOp.put("<=", "LT_EQ");
  }

  private static final Map<String, String> operatorNegations = new HashMap<>();

  private static void addNegation(final String op, final String negOp) {
    operatorNegations.put(op, negOp);
    operatorNegations.put(negOp, op);
  }

  static {
    addNegation("EQ", "NE");
    addNegation("GT", "LT_EQ");
    addNegation("LT", "GT_EQ");
    addNegation("CONTAINS", "NOT_CONTAINS");
    addNegation("STARTS_WITH", "NOT_STARTS_WITH");
    addNegation("ENDS_WITH", "NOT_ENDS_WITH");
    addNegation("REGEX", "NOT_REGEX");
  }

  private static class LiteralResolver extends OpsqlBaseVisitor<ResourceRequest.Condition> {
    private final ResourceRequest.Condition cond;

    public LiteralResolver(final ResourceRequest.Condition cond) {
      this.cond = cond;
    }

    @Override
    public ResourceRequest.Condition visitStringLiteral(
        final OpsqlParser.StringLiteralContext ctx) {
      cond.setStringValue(ctx.getText());
      return super.visitStringLiteral(ctx);
    }

    @Override
    public ResourceRequest.Condition visitNumber(final OpsqlParser.NumberContext ctx) {
      cond.setDoubleValue(Double.parseDouble(ctx.getText()));
      return super.visitNumber(ctx);
    }
  }

  private static class IdentifierResolver extends OpsqlBaseVisitor<Config.Field> {

    public static Config.Field resolveAny(final ParseTree ctx) {
      final Config.Field f = ctx.accept(new IdentifierResolver());
      return f;
    }

    public static Config.Field resolveProperty(final ParseTree ctx) {
      final Config.Field f = resolveAny(ctx);
      if (!f.hasProp()) {
        throw new RuntimeException("Expected property, got metric");
      }
      return f;
    }

    public static Config.Field resolveMetric(final ParseTree ctx) {
      final Config.Field f = resolveAny(ctx);
      if (!f.hasMetric()) {
        throw new RuntimeException("Expected metric, got property");
      }
      return f;
    }

    public IdentifierResolver() {}

    @Override
    public Config.Field visitMetricIdentifier(final OpsqlParser.MetricIdentifierContext ctx) {
      final Config.Field f = new Config.Field();
      f.setMetric(ctx.Identifier().getText());
      return f;
    }

    @Override
    public Config.Field visitPropertyIdentifier(final OpsqlParser.PropertyIdentifierContext ctx) {
      final Config.Field f = new Config.Field();
      f.setProp(ctx.PropertyIdentifier().getText().substring(1));
      return f;
    }
  }

  private static class FieldResolver extends OpsqlBaseVisitor<Config.Field> {
    @Override
    public Config.Field visitSimpleField(final OpsqlParser.SimpleFieldContext ctx) {
      final Config.Field f = new Config.Field();
      return super.visitSimpleField(ctx);
    }
  }

  private final Query query = new Query();

  private Mode mode;

  public QueryBuilderVisitor() {
    super();
  }

  private ResourceRequest.FilterSpec getConditions() {
    if (mode == Mode.property) {
      return query.resourceRequest.getPropertyConditions();
    } else {
      return query.resourceRequest.getStatConditions();
    }
  }

  private ResourceRequest.FilterSpec makeFilter() {
    final ResourceRequest.FilterSpec fs = new ResourceRequest.FilterSpec();
    fs.setConditions(new LinkedList<>());
    return fs;
  }

  @Override
  public Object visitQueryStatement(final OpsqlParser.QueryStatementContext ctx) {
    final String resource = ctx.resource.getText();
    final int p = resource.indexOf(":");
    if (p == -1) {
      query.getResourceRequest().setResourceKind(List.of(resource));
    } else {
      query.getResourceRequest().setAdapterKind(List.of(resource.substring(0, p)));
      query.getResourceRequest().setResourceKind(List.of(resource.substring(p + 1)));
    }
    return super.visitQueryStatement(ctx);
  }

  @Override
  public Object visitSimpleField(final OpsqlParser.SimpleFieldContext ctx) {
    query.getFields().add(IdentifierResolver.resolveAny(ctx));
    return super.visitSimpleField(ctx);
  }

  @Override
  public Object visitAliasedField(final OpsqlParser.AliasedFieldContext ctx) {
    final Config.Field f = IdentifierResolver.resolveAny(ctx);
    f.setAlias(ctx.alias.getText());
    return super.visitAliasedField(ctx);
  }

  // ***************** Filters *****************

  @Override
  public Object visitWhereMetrics(final OpsqlParser.WhereMetricsContext ctx) {
    mode = Mode.metric;
    query.resourceRequest.setStatConditions(makeFilter());
    return super.visitWhereMetrics(ctx);
  }

  @Override
  public Object visitWhereProperties(final OpsqlParser.WherePropertiesContext ctx) {
    mode = Mode.property;
    query.resourceRequest.setPropertyConditions(makeFilter());
    return super.visitWhereProperties(ctx);
  }

  @Override
  public Object visitAndExpression(final OpsqlParser.AndExpressionContext ctx) {
    getConditions().setConjunctionOperator("AND");
    return super.visitAndExpression(ctx);
  }

  @Override
  public Object visitOrExpression(final OpsqlParser.OrExpressionContext ctx) {
    getConditions().setConjunctionOperator("OR");
    return super.visitOrExpression(ctx);
  }

  @Override
  public Object visitNormalComparison(final OpsqlParser.NormalComparisonContext ctx) {
    final ResourceRequest.Condition cond = new ResourceRequest.Condition();
    cond.setKey(IdentifierResolver.resolveAny(ctx.propertyOrMetricIdentifier()).getName());
    cond.setOperator(toInternalOp.get(ctx.BooleanOperator().getText()));
    ctx.accept(new LiteralResolver(cond));
    return null; // No need to go deeper
  }

  @Override
  public Object visitNegatedComparison(final OpsqlParser.NegatedComparisonContext ctx) {
    final ResourceRequest.Condition cond = new ResourceRequest.Condition();
    cond.setKey(IdentifierResolver.resolveAny(ctx.propertyOrMetricIdentifier()).getName());
    cond.setOperator(operatorNegations.get(toInternalOp.get(ctx.BooleanOperator().getText())));
    ctx.accept(new LiteralResolver(cond));
    return null; // No need to go deeper
  }

  public Query getQuery() {
    return query;
  }
}
