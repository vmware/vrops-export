package com.vmware.vropsexport.models;

public class ResourceRequest {
  private String[] adapterKind;
  private FilterSpec propertyConditions;
  private String[] resourceKind;
  private String[] resourceId;
  private String[] regex;
  private String[] name;
  private String[] id;
  private FilterSpec statConditions;
  private String[] resourceState;
  private String[] resourceStatus;
  private String[] resourceHealth;
  private TagSpec[] resourceTag;

  public String[] getAdapterKind() {
    return adapterKind;
  }

  public void setAdapterKind(String[] adapterKind) {
    this.adapterKind = adapterKind;
  }

  public FilterSpec getPropertyConditions() {
    return propertyConditions;
  }

  public void setPropertyConditions(FilterSpec propertyConditions) {
    this.propertyConditions = propertyConditions;
  }

  public String[] getResourceKind() {
    return resourceKind;
  }

  public void setResourceKind(String[] resourceKind) {
    this.resourceKind = resourceKind;
  }

  public String[] getResourceId() {
    return resourceId;
  }

  public void setResourceId(String[] resourceId) {
    this.resourceId = resourceId;
  }

  public String[] getRegex() {
    return regex;
  }

  public void setRegex(String[] regex) {
    this.regex = regex;
  }

  public String[] getName() {
    return name;
  }

  public void setName(String[] name) {
    this.name = name;
  }

  public String[] getId() {
    return id;
  }

  public void setId(String[] id) {
    this.id = id;
  }

  public FilterSpec getStatConditions() {
    return statConditions;
  }

  public void setStatConditions(FilterSpec statConditions) {
    this.statConditions = statConditions;
  }

  public String[] getResourceState() {
    return resourceState;
  }

  public void setResourceState(String[] resourceState) {
    this.resourceState = resourceState;
  }

  public String[] getResourceStatus() {
    return resourceStatus;
  }

  public void setResourceStatus(String[] resourceStatus) {
    this.resourceStatus = resourceStatus;
  }

  public String[] getResourceHealth() {
    return resourceHealth;
  }

  public void setResourceHealth(String[] resourceHealth) {
    this.resourceHealth = resourceHealth;
  }

  public TagSpec[] getResourceTag() {
    return resourceTag;
  }

  public void setResourceTag(TagSpec[] resourceTag) {
    this.resourceTag = resourceTag;
  }

  public static class Condition {
    private double doubleValue;
    private String stringValue;
    private String key;
    private String operato;

    public double getDoubleValue() {
      return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
      this.doubleValue = doubleValue;
    }
  }

  public static class FilterSpec {
    private Condition[] conditions;
    private String conjunctionOperator;

    public Condition[] getConditions() {
      return conditions;
    }

    public void setConditions(Condition[] conditions) {
      this.conditions = conditions;
    }
  }

  public static class TagSpec {
    private String category;
    private String name;

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }
  }
}
