package com.vmware.vropsexport;

import com.vmware.vropsexport.models.*;
import org.apache.http.HttpException;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.regex.Matcher;
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

  public void printResourceMetadata(final String adapterAndResourceKind, final PrintStream out)
      throws IOException, HttpException {
    String resourceKind = adapterAndResourceKind;
    String adapterKind = "VMWARE";
    final Matcher m = Patterns.adapterAndResourceKindPattern.matcher(adapterAndResourceKind);
    if (m.matches()) {
      adapterKind = m.group(1);
      resourceKind = m.group(2);
    }
    final StatKeysResponse response =
        client.getJson(
            "/suite-api/api/adapterkinds/"
                + Exporter.urlencode(adapterKind)
                + "/resourcekinds/"
                + Exporter.urlencode(resourceKind)
                + "/statkeys",
            StatKeysResponse.class);
    for (final StatKeysResponse.StatKey key : response.getStatKeys()) {
      out.println("Key  : " + key.getKey());
      out.println("Name : " + key.getName());
      out.println();
    }
  }

  public List<String> getStatKeysForResourceKind(
      final String adapterKind, final String resourceKind) throws IOException, HttpException {
    final StatKeysResponse response =
        client.getJson(
            "/suite-api/api/adapterkinds/"
                + Exporter.urlencode(adapterKind)
                + "/resourcekinds/"
                + Exporter.urlencode(resourceKind)
                + "/statkeys",
            StatKeysResponse.class);

    return response.getStatKeys().stream()
        .map(StatKeysResponse.StatKey::getKey)
        .collect(Collectors.toList());
  }

  public List<String> getStatKeysForResource(final String resourceId)
      throws IOException, HttpException {
    final ResourceStatKeysResponse response =
        client.getJson(
            "/suite-api/api/resources/" + resourceId + "/statkeys", ResourceStatKeysResponse.class);
    return response.getStatKeys().stream().map(r -> r.get("key")).collect(Collectors.toList());
  }
}
