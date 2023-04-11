package com.vmware.vropsexport.opsql;

import com.vmware.vropsexport.Config;
import com.vmware.vropsexport.models.ResourceRequest;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Query {
  final ResourceRequest resourceRequest;

  final List<Config.Field> fields;

  public Query() {
    resourceRequest = new ResourceRequest();
    fields = new LinkedList<>();
  }

  public Query(final ResourceRequest resourceRequest, final List<Config.Field> fields) {
    this.resourceRequest = resourceRequest;
    this.fields = fields;
  }

  public ResourceRequest getResourceRequest() {
    return resourceRequest;
  }

  public List<Config.Field> getFields() {
    return fields;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final Query query = (Query) o;
    return Objects.equals(resourceRequest, query.resourceRequest)
        && Objects.equals(fields, query.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(resourceRequest, fields);
  }
}
