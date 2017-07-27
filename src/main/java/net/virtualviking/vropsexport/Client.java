/* 
 * Copyright 2017 Pontus Rydin
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
package net.virtualviking.vropsexport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import net.virtualviking.vropsexport.security.ExtendableTrustStrategy;
import net.virtualviking.vropsexport.security.RecoverableCertificateException;

public class Client {	
	private static Log log = LogFactory.getLog(Client.class);

	private static final int CONNTECTION_TIMEOUT_MS = 60000;

	private static final int CONNECTION_REQUEST_TIMEOUT_MS = 60000;

	private static final int SOCKET_TIMEOUT_MS = 60000;

	private final HttpClient client;

	private final String urlBase;

	private final  LRUCache<String, JSONObject> jsonCache = new LRUCache<>(1000);

	private String authToken;

	private Certificate[] peerCerts;
	
	public Client(String urlBase, String username, String password, int threads, KeyStore extendedTrust) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, IOException, HttpException, ExporterException {
		this.urlBase = urlBase;
		// Configure timeout
		//
		final RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(CONNTECTION_TIMEOUT_MS)
				.setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MS)
				.setSocketTimeout(SOCKET_TIMEOUT_MS)
				.build();
		
		ExtendableTrustStrategy extendedTrustStrategy = new ExtendableTrustStrategy(extendedTrust);
		SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, extendedTrustStrategy).build();
		SSLConnectionSocketFactory sslf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslf).build();
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
		cm.setMaxTotal(20);
		cm.setDefaultMaxPerRoute(20);
		this.client = HttpClients.custom().
				setSSLSocketFactory(sslf).
				setConnectionManager(cm).
				setDefaultRequestConfig(requestConfig).
				//setRetryHandler(new DefaultHttpRequestRetryHandler(3, false)).
				setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE).build();

		// Authenticate
		//
		try {
			JSONObject rq = new JSONObject();
			rq.put("username", username);
			rq.put("password", password);
			InputStream is = this.postJsonReturnStream("/suite-api/api/auth/token/acquire", rq);
			try {
				JSONObject response = new JSONObject(IOUtils.toString(is, Charset.defaultCharset()));
				this.authToken = response.getString("token");
			} finally {
				is.close();
			}
		} catch(SSLHandshakeException e) {
			// If we captured a cert, it's recoverable by asking the user to trust it.
			//
			X509Certificate[] cc = extendedTrustStrategy.getCapturedCerts();
			if(cc == null)
				throw e;
			throw new RecoverableCertificateException(cc, e);
		}
	}

	public JSONObject getJson(String uri, String ...queries) throws IOException, HttpException {
		if(queries != null) {
			for(int i = 0; i < queries.length; ++i) {
				uri += i == 0 ? '?' : '&';
				uri += queries[i];
			}
		}
		synchronized(jsonCache) {
			if(jsonCache.containsKey(uri)) {
				return jsonCache.get(uri);
			}
		}
		HttpGet get = new HttpGet(urlBase + uri);
		get.addHeader("Accept", "application/json");
		if(this.authToken != null)
			get.addHeader("Authorization", "vRealizeOpsToken " + this.authToken + "");
		HttpResponse resp = client.execute(get);
		this.checkResponse(resp);
		JSONObject result = new JSONObject(EntityUtils.toString(resp.getEntity()));
		synchronized(jsonCache) {
			jsonCache.put(uri, result);
		}
		return result;
	}

	public InputStream postJsonReturnStream(String uri, JSONObject payload) throws IOException, HttpException {
		HttpPost post = new HttpPost(urlBase + uri);
		post.setEntity(new StringEntity(payload.toString()));
		//System.err.println(payload.toString());
		post.addHeader("Accept", "application/json");
		post.addHeader("Content-Type", "application/json");
		if(this.authToken != null)
			post.addHeader("Authorization", "vRealizeOpsToken " + this.authToken + "");
		HttpResponse resp = client.execute(post);
		this.checkResponse(resp);
		return resp.getEntity().getContent();
	}

	public JSONObject getJson(String uri, List<String> queries) throws IOException, HttpException {
		String[] s;
		if(queries != null) {
			s = new String[queries.size()];
			queries.toArray(s);
		} else
			s = new String[0];
		return this.getJson(uri, s);
	}
	
	private HttpResponse checkResponse(HttpResponse response)
			throws HttpException, UnsupportedOperationException, IOException {
		int status = response.getStatusLine().getStatusCode();
		log.debug("HTTP status: " + status);
		if (status == 200 || status == 201)
			return response;
		log.debug("Error response from server: "
				+ IOUtils.toString(response.getEntity().getContent(), Charset.defaultCharset()));
		throw new HttpException("HTTP Error: " + response.getStatusLine().getStatusCode() + " Reason: "
				+ response.getStatusLine().getReasonPhrase());
	}
}