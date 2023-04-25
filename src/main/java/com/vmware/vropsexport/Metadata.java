package com.vmware.vropsexport;

import com.vmware.vropsexport.models.*;
import org.apache.http.HttpException;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class Metadata {
  private final Client client;

  public Metadata(final Client client) {
    this.client = client;
  }

  public List<AdapterKind> getAdapterKinds() throws HttpException, IOException {
    return client
        .getJson("/suite-api/api/adapterkinds", AdapterKindResponse.class)
        .getAdapterKind();
  }

  public List<ResourceKind> getResourceKinds(final String adapterKind)
      throws IOException, HttpException {
    return client
        .getJson(
            "/suite-api/api/adapterkinds/" + Exporter.urlencode(adapterKind) + "/resourcekinds",
            ResourceKindResponse.class)
        .getResourceKinds();
  }

  public List<StatKeysResponse.StatKey> getStatKeysForResourceKind(
      final String adapterKind, final String resourceKind) throws IOException, HttpException {
    return client
        .getJson(
            "/suite-api/api/adapterkinds/"
                + Exporter.urlencode(adapterKind)
                + "/resourcekinds/"
                + Exporter.urlencode(resourceKind)
                + "/statkeys",
            StatKeysResponse.class)
        .getStatKeys();
  }

  public List<String> getStatKeysForResource(final String resourceId)
      throws IOException, HttpException {
    final ResourceStatKeysResponse response =
        client.getJson(
            "/suite-api/api/resources/" + resourceId + "/statkeys", ResourceStatKeysResponse.class);
    return response.getStatKeys().stream().map(r -> r.get("key")).collect(Collectors.toList());
  }
}
