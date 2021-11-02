/*
 * Copyright 2017-2021 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vmware.vrops.client.model.AuthToken;
import com.vmware.vrops.client.model.UsernamePassword;
import com.vmware.vropsexport.exceptions.ExporterException;
import com.vmware.vropsexport.security.ExtendableTrustStrategy;
import com.vmware.vropsexport.security.RecoverableCertificateException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class Client {
  private static final Logger log = LogManager.getLogger(Client.class);

  private static final int CONNTECTION_TIMEOUT_MS = 60000;

  private static final int CONNECTION_REQUEST_TIMEOUT_MS = 60000;

  private static final int SOCKET_TIMEOUT_MS = 300000;

  private final HttpClient client;

  private final String urlBase;

  private final String authToken;

  private final boolean dumpRest;

  public Client(
      final String urlBase,
      String username,
      final String password,
      final KeyStore extendedTrust,
      final boolean dumpRest)
      throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException,
          HttpException, ExporterException {
    this.urlBase = urlBase;
    this.dumpRest = dumpRest;
    // Configure timeout
    //
    final RequestConfig requestConfig =
        RequestConfig.custom()
            .setConnectTimeout(CONNTECTION_TIMEOUT_MS)
            .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MS)
            .setSocketTimeout(SOCKET_TIMEOUT_MS)
            .build();

    final ExtendableTrustStrategy extendedTrustStrategy =
        new ExtendableTrustStrategy(extendedTrust);
    final SSLContext sslContext =
        SSLContexts.custom().loadTrustMaterial(null, extendedTrustStrategy).build();
    final SSLConnectionSocketFactory sslf =
        new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
    final Registry<ConnectionSocketFactory> socketFactoryRegistry =
        RegistryBuilder.<ConnectionSocketFactory>create().register("https", sslf).build();
    final PoolingHttpClientConnectionManager cm =
        new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    cm.setMaxTotal(20);
    cm.setDefaultMaxPerRoute(20);
    client =
        HttpClients.custom()
            .setSSLSocketFactory(sslf)
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .
            // setRetryHandler(new DefaultHttpRequestRetryHandler(3, false)).
            setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            .build();

    // Authenticate
    //
    try {
      // User may be in a non-local auth source.
      //
      int p = username.indexOf('\\');
      String authSource = null;
      if (p != -1) {
        authSource = username.substring(0, p);
        username = username.substring(p + 1);
      } else {
        p = username.indexOf('@');
        if (p != -1) {
          authSource = username.substring(p + 1);
          username = username.substring(0, p);
        }
      }
      final UsernamePassword rq =
          new UsernamePassword().username(username).password(password).authSource(authSource);
      final AuthToken response =
          postJsonReturnJson("/suite-api/api/auth/token/acquire", rq, AuthToken.class);
      authToken = response.getToken();
    } catch (final SSLHandshakeException e) {
      // If we captured a cert, it's recoverable by asking the user to trust it.
      //
      final X509Certificate[] cc = extendedTrustStrategy.getCapturedCerts();
      if (cc == null) {
        throw e;
      }
      throw new RecoverableCertificateException(cc, e);
    }
  }

  public <T> T getJson(final String uri, final Class<T> responseClass, final String... queries)
      throws IOException, HttpException {
    final HttpResponse resp = innerGet(uri, queries);
    return getObjectMapper().readValue(resp.getEntity().getContent(), responseClass);
  }

  private HttpResponse innerGet(String uri, final String... queries)
      throws IOException, HttpException {
    if (queries != null) {
      final StringBuilder sb = new StringBuilder(uri);
      for (int i = 0; i < queries.length; ++i) {
        sb.append(i == 0 ? '?' : '&');
        sb.append(queries[i]);
      }
      uri = sb.toString();
    }
    if (dumpRest) {
      log.debug("GET " + urlBase + uri);
    }
    final HttpGet get = new HttpGet(urlBase + uri);
    get.addHeader("Accept", "application/json");
    get.addHeader("Accept-Encoding", "gzip");
    if (authToken != null) {
      get.addHeader("Authorization", "vRealizeOpsToken " + authToken + "");
    }
    final HttpResponse resp = client.execute(get);
    checkResponse(resp);
    return resp;
  }

  public InputStream postJsonReturnStream(final String uri, final Object payload)
      throws IOException, HttpException {
    final HttpPost post = new HttpPost(urlBase + uri);
    post.setEntity(new StringEntity(getObjectMapper().writeValueAsString(payload)));
    post.addHeader("Accept", "application/json");
    post.addHeader("Content-Type", "application/json");
    post.addHeader("Accept-Encoding", "gzip");
    if (authToken != null) {
      post.addHeader("Authorization", "vRealizeOpsToken " + authToken + "");
    }
    if (dumpRest) {
      log.debug("POST " + urlBase + uri);
    }
    final HttpResponse resp = client.execute(post);
    checkResponse(resp);
    return resp.getEntity().getContent();
  }

  public <T> T postJsonReturnJson(
      final String uri, final Object payload, final Class<T> responseClass)
      throws IOException, HttpException {
    return getObjectMapper().readValue(postJsonReturnStream(uri, payload), responseClass);
  }

  public <T> T getJson(final String uri, final List<String> queries, final Class<T> responseClass)
      throws IOException, HttpException {
    return getJson(uri, responseClass, packQueries(queries));
  }

  public InputStream getStream(final String uri, final List<String> queries)
      throws IOException, HttpException {
    return getStream(uri, packQueries(queries));
  }

  public InputStream getStream(final String uri, final String... queries)
      throws IOException, HttpException {
    final HttpResponse resp = innerGet(uri, queries);
    return resp.getEntity().getContent();
  }

  private String[] packQueries(final List<String> queries) {
    final String[] s;
    if (queries != null) {
      s = new String[queries.size()];
      queries.toArray(s);
    } else {
      s = new String[0];
    }
    return s;
  }

  private HttpResponse checkResponse(final HttpResponse response)
      throws HttpException, UnsupportedOperationException, IOException {
    final int status = response.getStatusLine().getStatusCode();
    log.debug("HTTP status: " + status);
    if (status == 200 || status == 201) {
      return response;
    }
    log.debug(
        "Error response from server: "
            + IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
    throw new HttpException(
        "HTTP Error: "
            + response.getStatusLine().getStatusCode()
            + " Reason: "
            + response.getStatusLine().getReasonPhrase());
  }

  private ObjectMapper getObjectMapper() {
    return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}
