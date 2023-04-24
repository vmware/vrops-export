package com.vmware.vropsexport.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourceRequest {
  private List<String> adapterKind;
  private FilterSpec propertyConditions;
  private List<String> resourceKind;
  private List<String> resourceId;
  private List<String> regex;
  private List<String> name;
  private List<String> id;
  private FilterSpec statConditions;
  private List<String> resourceState;
  private List<String> resourceStatus;
  private List<String> resourceHealth;
  private List<TagSpec> resourceTag;

  public List<String> getAdapterKind() {
    return adapterKind;
  }

  public void setAdapterKind(final List<String> adapterKind) {
    this.adapterKind = adapterKind;
  }

  public FilterSpec getPropertyConditions() {
    return propertyConditions;
  }

  public void setPropertyConditions(final FilterSpec propertyConditions) {
    this.propertyConditions = propertyConditions;
  }

  public List<String> getResourceKind() {
    return resourceKind;
  }

  public void setResourceKind(final List<String> resourceKind) {
    this.resourceKind = resourceKind;
  }

  public List<String> getResourceId() {
    return resourceId;
  }

  public void setResourceId(final List<String> resourceId) {
    this.resourceId = resourceId;
  }

  public List<String> getRegex() {
    return regex;
  }

  public void setRegex(final List<String> regex) {
    this.regex = regex;
  }

  public List<String> getName() {
    return name;
  }

  public void setName(final List<String> name) {
    this.name = name;
  }

  public List<String> getId() {
    return id;
  }

  public void setId(final List<String> id) {
    this.id = id;
  }

  public FilterSpec getStatConditions() {
    return statConditions;
  }

  public void setStatConditions(final FilterSpec statConditions) {
    this.statConditions = statConditions;
  }

  public List<String> getResourceState() {
    return resourceState;
  }

  public void setResourceState(final List<String> resourceState) {
    this.resourceState = resourceState;
  }

  public List<String> getResourceStatus() {
    return resourceStatus;
  }

  public void setResourceStatus(final List<String> resourceStatus) {
    this.resourceStatus = resourceStatus;
  }

  public List<String> getResourceHealth() {
    return resourceHealth;
  }

  public void setResourceHealth(final List<String> resourceHealth) {
    this.resourceHealth = resourceHealth;
  }

  public List<TagSpec> getResourceTag() {
    return resourceTag;
  }

  public void setResourceTag(final List<TagSpec> resourceTag) {
    this.resourceTag = resourceTag;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ResourceRequest that = (ResourceRequest) o;
    return Objects.equals(adapterKind, that.adapterKind)
        && Objects.equals(propertyConditions, that.propertyConditions)
        && Objects.equals(resourceKind, that.resourceKind)
        && Objects.equals(resourceId, that.resourceId)
        && Objects.equals(regex, that.regex)
        && Objects.equals(name, that.name)
        && Objects.equals(id, that.id)
        && Objects.equals(statConditions, that.statConditions)
        && Objects.equals(resourceState, that.resourceState)
        && Objects.equals(resourceStatus, that.resourceStatus)
        && Objects.equals(resourceHealth, that.resourceHealth)
        && Objects.equals(resourceTag, that.resourceTag);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        adapterKind,
        propertyConditions,
        resourceKind,
        resourceId,
        regex,
        name,
        id,
        statConditions,
        resourceState,
        resourceStatus,
        resourceHealth,
        resourceTag);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class Condition {
    private Double doubleValue;
    private String stringValue;
    private String key;
    private String operator;

    public Double getDoubleValue() {
      return doubleValue;
    }

    public void setDoubleValue(final double doubleValue) {
      this.doubleValue = doubleValue;
    }

    public String getStringValue() {
      return stringValue;
    }

    public void setStringValue(final String stringValue) {
      this.stringValue = stringValue;
    }

    public String getKey() {
      return key;
    }

    public void setKey(final String key) {
      this.key = key;
    }

    public String getOperator() {
      return operator;
    }

    public void setOperator(final String operator) {
      this.operator = operator;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Condition condition = (Condition) o;
      return Double.compare(condition.doubleValue, doubleValue) == 0
          && Objects.equals(stringValue, condition.stringValue)
          && Objects.equals(key, condition.key)
          && Objects.equals(operator, condition.operator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(doubleValue, stringValue, key, operator);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class FilterSpec {
    private List<Condition> conditions;
    private String conjunctionOperator;

    public List<Condition> getConditions() {
      return conditions;
    }

    public void setConditions(final List<Condition> conditions) {
      this.conditions = conditions;
    }

    public String getConjunctionOperator() {
      return conjunctionOperator;
    }

    public void setConjunctionOperator(final String conjunctionOperator) {
      this.conjunctionOperator = conjunctionOperator;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final FilterSpec that = (FilterSpec) o;
      return Objects.equals(conditions, that.conditions)
          && Objects.equals(conjunctionOperator, that.conjunctionOperator);
    }

    @Override
    public int hashCode() {
      return Objects.hash(conditions, conjunctionOperator);
    }
  }

  public static class TagSpec {
    private String category;
    private String name;

    public String getCategory() {
      return category;
    }

    public void setCategory(final String category) {
      this.category = category;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final TagSpec tagSpec = (TagSpec) o;
      return Objects.equals(category, tagSpec.category) && Objects.equals(name, tagSpec.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(category, name);
    }
  }
}
