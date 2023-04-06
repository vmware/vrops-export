package com.vmware.vropsexport.opsql;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class QueryAST {
  private static final Map<String, String> operatorNegations = new HashMap<>();

  private static void addNegation(String op, String negOp) {
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

  public interface Visitor {
    void onVisit(Expression node);
  }

  public abstract static class Expression {
    private Expression parent;

    public Expression() {}

    public abstract Expression negate();

    public abstract Expression clone();

    public abstract void visit(Visitor v);

    public abstract void replaceChild(Expression old, Expression current);

    public Expression getParent() {
      return parent;
    }

    public void setParent(Expression parent) {
      this.parent = parent;
    }
  }

  public abstract static class Terminal extends Expression {
    @Override
    public Expression negate() {
      return this;
    }

    @Override
    public void visit(Visitor v) {
      v.onVisit(this);
    }

    @Override
    public void replaceChild(Expression old, Expression current) {
      throw new RuntimeException("replaceChild() called on terminal");
    }
  }

  public abstract static class BinaryExpression extends Expression {
    protected Expression left;
    protected Expression right;

    public BinaryExpression() {}

    public BinaryExpression(Expression left, Expression right) {
      this.left = left;
      this.right = right;
      left.setParent(this);
      right.setParent(this);
    }

    public Expression getLeft() {
      return left;
    }

    public void setLeft(Expression left) {
      this.left = left;
      left.setParent(this);
    }

    public Expression getRight() {
      return right;
    }

    public void setRight(Expression right) {
      this.right = right;
      right.setParent(this);
    }

    @Override
    public void visit(Visitor v) {
      v.onVisit(this);
      left.visit(v);
      right.visit(v);
    }

    @Override
    public void replaceChild(Expression old, Expression current) {
      assert old == left || old == right;
      if (old == left) {
        left = current;
      } else {
        right = current;
      }
      current.setParent(this);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BinaryExpression that = (BinaryExpression) o;
      return Objects.equals(left, that.left) && Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
      return Objects.hash(left, right);
    }
  }

  public abstract static class UnaryExpression extends Expression {
    protected Expression child;

    public UnaryExpression() {}

    public UnaryExpression(Expression child) {
      this.child = child;
    }

    public Expression getChild() {
      return child;
    }

    public void setChild(Expression child) {
      this.child = child;
      child.setParent(this);
    }

    @Override
    public void visit(Visitor v) {
      v.onVisit(this);
      this.child.visit(v);
    }

    @Override
    public void replaceChild(Expression old, Expression current) {
      assert old == child;
      child = current;
      current.setParent(this);
    }

    @Override
    public int hashCode() {
      return Objects.hash(child);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UnaryExpression that = (UnaryExpression) o;
      return Objects.equals(child, that.child);
    }
  }

  public static class Not extends UnaryExpression {
    public Not() {}

    public Not(Expression expression) {
      super(expression);
    }

    @Override
    public Expression negate() {
      return child instanceof Not ? ((Not) child).getChild() : child.negate();
    }

    @Override
    public Expression clone() {
      return new Not(child.clone());
    }

    @Override
    public String toString() {
      return "not (" + child + ")";
    }
  }

  public static class And extends BinaryExpression {
    public And() {}

    public And(Expression left, Expression right) {
      super(left, right);
    }

    // not (a and b) == not a or not b
    @Override
    public Expression negate() {
      return new Or(left.negate(), right.negate());
    }

    @Override
    public Expression clone() {
      return new And(left.clone(), right.clone());
    }

    @Override
    public String toString() {
      return "(" + left + " and " + right + ")";
    }
  }

  public static class Or extends BinaryExpression {
    public Or() {}

    public Or(Expression left, Expression right) {
      super(left, right);
    }

    // not (a or b) == not b and not a
    @Override
    public Expression negate() {
      return new And(left.negate(), right.negate());
    }

    @Override
    public Expression clone() {
      return new Or(left.clone(), right.clone());
    }

    @Override
    public String toString() {
      return "(" + left + " or " + right + ")";
    }
  }

  public static class Comparison extends BinaryExpression {
    private final String operator;

    public Comparison(String operator) {
      this.operator = operator;
    }

    public Comparison(Expression left, Expression right, String operator) {
      super(left, right);
      this.operator = operator;
    }

    @Override
    public Expression negate() {
      return new Comparison(left, right, operatorNegations.get(operator));
    }

    @Override
    public Expression clone() {
      return new Comparison(left.clone(), right.clone(), operator);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      Comparison that = (Comparison) o;
      return Objects.equals(operator, that.operator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), operator);
    }

    @Override
    public String toString() {
      return left + " " + operator + " " + right;
    }
  }

  public static class Identifier extends Terminal {
    private String name;

    public Identifier(String name) {
      this.name = name;
    }

    public Expression clone() {
      return new Identifier(name);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Identifier that = (Identifier) o;
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class StringLiteral extends Terminal {
    private String value;

    public StringLiteral(String value) {
      this.value = value;
    }

    public Expression clone() {
      return new StringLiteral(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StringLiteral that = (StringLiteral) o;
      return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return "\"" + value + "\"";
    }
  }

  public static class DoubleLiteral extends Terminal {
    private double value;

    public DoubleLiteral(double value) {
      this.value = value;
    }

    public Expression clone() {
      return new DoubleLiteral(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DoubleLiteral that = (DoubleLiteral) o;
      return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value);
    }

    @Override
    public String toString() {
      return Double.toString(value);
    }
  }
}
