/*
 * Copyright 2017-2023 VMware, Inc. All Rights Reserved.
 *
 * SPDX-License-Identifier:	Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Field;
import com.vmware.vropsexport.models.ResourceRequest;

import java.util.*;

public class QueryBuilderVisitor extends OpsqlBaseVisitor<Object> {

  private static class Relationship {
    private String adapterKind;
    private final String resourceKind;
    private final Field.RelationshipType type;
    private final int depth;

    public Relationship(
        final String resourceKind, final Field.RelationshipType type, final int depth) {
      this.type = type;
      this.depth = depth;
      final int p = resourceKind.indexOf(":");
      if (p == -1) {
        this.resourceKind = resourceKind;
      } else {
        adapterKind = resourceKind.substring(0, p);
        this.resourceKind = resourceKind.substring(p + 1);
      }
    }
  }

  private static final Map<String, String> toInternalOp = new HashMap<>();

  static {
    toInternalOp.put("=", "EQ");
    toInternalOp.put("!=", "NOT_EQ");
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
    addNegation("EQ", "NOT_EQ");
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
      cond.setStringValue(unquote(ctx.getText()));
      return super.visitStringLiteral(ctx);
    }

    @Override
    public ResourceRequest.Condition visitNumber(final OpsqlParser.NumberContext ctx) {
      cond.setDoubleValue(Double.parseDouble(ctx.getText()));
      return super.visitNumber(ctx);
    }
  }

  private class IdentifierResolver extends OpsqlBaseVisitor<Field> {
    public IdentifierResolver() {}

    @Override
    public Field visitMetricIdentifier(final OpsqlParser.MetricIdentifierContext ctx) {
      final Field f = new Field();
      f.setMetric(ctx.Identifier().getText());
      return f;
    }

    @Override
    public Field visitRelativeMetricIdentifier(
        final OpsqlParser.RelativeMetricIdentifierContext ctx) {
      final Field f = new Field();
      f.setMetric(ctx.resource.getText() + "." + ctx.field.getText());
      fillInField(f, ctx.resource.getText(), ctx.field.getText(), ctx.aggregation().getText());
      return f;
    }

    @Override
    public Field visitPropertyIdentifier(final OpsqlParser.PropertyIdentifierContext ctx) {
      final Field f = new Field();
      f.setProp(ctx.PropertyIdentifier().getText().substring(1));
      return f;
    }

    @Override
    public Field visitRelativePropertyIdentifier(
        final OpsqlParser.RelativePropertyIdentifierContext ctx) {
      final Field f = new Field();
      f.setProp(ctx.resource.getText() + "." + ctx.field.getText());
      fillInField(f, ctx.resource.getText(), ctx.field.getText(), ctx.aggregation().getText());
      return f;
    }
  }

  private void fillInField(
      final Field f, final String resourceAlias, final String fieldName, final String aggregation) {
    f.setLocalName(fieldName);
    final Relationship rel = relationshipAliases.get(resourceAlias);
    if (rel == null) {
      throw new OpsqlException("Reference to unknown alias '" + resourceAlias + "'");
    }
    f.setAggregation(Field.AggregationType.valueOf(aggregation));
    f.setSearchDepth(rel.depth);
    f.setRelationshipType(rel.type);
    f.setRelatedAdapterKind(rel.adapterKind);
    f.setRelatedResourceKind(rel.resourceKind);
  }

  private class BooleanExpressionVisitor extends OpsqlBaseVisitor<Object> {
    private final ResourceRequest.FilterSpec spec = new ResourceRequest.FilterSpec();

    public BooleanExpressionVisitor() {
      spec.setConditions(new LinkedList<>());
    }

    @Override
    public Object visitAndExpression(final OpsqlParser.AndExpressionContext ctx) {
      spec.setConjunctionOperator("AND");
      return super.visitAndExpression(ctx);
    }

    @Override
    public Object visitOrExpression(final OpsqlParser.OrExpressionContext ctx) {
      spec.setConjunctionOperator("OR");
      return super.visitOrExpression(ctx);
    }

    @Override
    public Object visitSimpleExpression(final OpsqlParser.SimpleExpressionContext ctx) {
      spec.setConjunctionOperator("AND");
      return super.visitSimpleExpression(ctx);
    }

    @Override
    public Object visitNormalComparison(final OpsqlParser.NormalComparisonContext ctx) {
      final ResourceRequest.Condition cond = new ResourceRequest.Condition();
      cond.setKey(ctx.propertyOrMetricIdentifier().accept(new IdentifierResolver()).getName());
      cond.setOperator(toInternalOp.get(ctx.booleanOperator().getText()));
      ctx.accept(new LiteralResolver(cond));
      spec.getConditions().add(cond);
      return null; // No need to go deeper
    }

    @Override
    public Object visitNegatedComparison(final OpsqlParser.NegatedComparisonContext ctx) {
      final ResourceRequest.Condition cond = new ResourceRequest.Condition();
      cond.setKey(ctx.propertyOrMetricIdentifier().accept(new IdentifierResolver()).getName());
      cond.setOperator(operatorNegations.get(toInternalOp.get(ctx.booleanOperator().getText())));
      ctx.accept(new LiteralResolver(cond));
      spec.getConditions().add(cond);
      return null; // No need to go deeper
    }

    public ResourceRequest.FilterSpec getSpec() {
      return spec;
    }
  }

  private final Query query = new Query();

  private final Map<String, Relationship> relationshipAliases = new HashMap<>();

  public QueryBuilderVisitor() {
    super();
  }

  private static String unquote(final String s) {
    if (s.length() == 2) {
      return "";
    }
    if (s.length() < 2) {
      throw new IllegalArgumentException("Internal error: Quoted string has no quotes");
    }
    return s.substring(1, s.length() - 1);
  }

  private static List<String> extractStringList(final OpsqlParser.StringLiteralListContext ctx) {
    final List<String> s = new ArrayList<String>(ctx.children.size());
    for (int i = 0; i < ctx.children.size(); i += 2) {
      s.add(unquote(ctx.getChild(i).getText()));
    }
    return s;
  }

  private ResourceRequest.FilterSpec resolveFilter(final OpsqlParser.FilterContext ctx) {
    final BooleanExpressionVisitor v = new BooleanExpressionVisitor();
    ctx.accept(v);
    return v.getSpec();
  }

  @Override
  public Object visitChildrenDeclaration(final OpsqlParser.ChildrenDeclarationContext ctx) {
    for (final OpsqlParser.RelationshipSpecifierContext rel :
        ctx.relatives.relationshipSpecifier()) {
      relationshipAliases.put(
          rel.alias.getText(),
          new Relationship(
              rel.resourceType.getText(),
              Field.RelationshipType.child,
              rel.depth != null ? Integer.parseInt(rel.depth.getText()) : 1));
    }
    return null; // No need to go deeper
  }

  @Override
  public Object visitParentsDeclaration(final OpsqlParser.ParentsDeclarationContext ctx) {
    for (final OpsqlParser.RelationshipSpecifierContext rel :
        ctx.relatives.relationshipSpecifier()) {
      relationshipAliases.put(
          rel.alias.getText(),
          new Relationship(
              rel.resourceType.getText(),
              Field.RelationshipType.parent,
              rel.depth != null ? Integer.parseInt(rel.depth.getText()) : 1));
    }
    return null; // No need to go deeper
  }

  @Override
  public Object visitQueryStatement(final OpsqlParser.QueryStatementContext ctx) {
    final String resource = ctx.resource.getText();
    final int p = resource.indexOf(":");
    if (p == -1) {
      query.getResourceRequest().setResourceKind(Collections.singletonList(resource));
    } else {
      query
          .getResourceRequest()
          .setAdapterKind(Collections.singletonList(resource.substring(0, p)));
      query
          .getResourceRequest()
          .setResourceKind(Collections.singletonList((resource.substring(p + 1))));
    }
    return super.visitQueryStatement(ctx);
  }

  @Override
  public Object visitSimpleField(final OpsqlParser.SimpleFieldContext ctx) {
    final Field f = ctx.accept(new IdentifierResolver());
    f.setAlias(f.getName());
    query.getFields().add(f);
    return super.visitSimpleField(ctx);
  }

  @Override
  public Object visitAliasedField(final OpsqlParser.AliasedFieldContext ctx) {
    final Field f = ctx.field.accept(new IdentifierResolver());
    f.setAlias(ctx.alias.getText());
    query.getFields().add(f);
    return super.visitAliasedField(ctx);
  }

  // ***************** Filters *****************

  @Override
  public Object visitWhereName(final OpsqlParser.WhereNameContext ctx) {
    query.resourceRequest.setName(extractStringList(ctx.stringLiteralList()));
    return super.visitWhereName(ctx);
  }

  @Override
  public Object visitWhereRegex(final OpsqlParser.WhereRegexContext ctx) {
    query.resourceRequest.setRegex(extractStringList(ctx.stringLiteralList()));
    return super.visitWhereRegex(ctx);
  }

  @Override
  public Object visitWhereHealth(final OpsqlParser.WhereHealthContext ctx) {
    query.resourceRequest.setResourceHealth(extractStringList(ctx.stringLiteralList()));
    return super.visitWhereHealth(ctx);
  }

  @Override
  public Object visitWhereState(final OpsqlParser.WhereStateContext ctx) {
    query.resourceRequest.setResourceState(extractStringList(ctx.stringLiteralList()));
    return super.visitWhereState(ctx);
  }

  @Override
  public Object visitWhereStatus(final OpsqlParser.WhereStatusContext ctx) {
    query.resourceRequest.setResourceStatus(extractStringList(ctx.stringLiteralList()));
    return super.visitWhereStatus(ctx);
  }

  @Override
  public Object visitWhereMetrics(final OpsqlParser.WhereMetricsContext ctx) {
    query.resourceRequest.setStatConditions(resolveFilter(ctx));
    return super.visitWhereMetrics(ctx);
  }

  @Override
  public Object visitWhereProperties(final OpsqlParser.WherePropertiesContext ctx) {
    query.resourceRequest.setPropertyConditions(resolveFilter(ctx));
    return super.visitWhereProperties(ctx);
  }

  public Query getQuery() {
    return query;
  }
}
