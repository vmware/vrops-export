/* 
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
package net.virtualviking.vropsexport.security;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.TrustStrategy;

public class ExtendableTrustStrategy implements TrustStrategy, org.apache.http.ssl.TrustStrategy {	
	private final TrustManager[] trustManagers;
	
	private X509Certificate[] capturedCerts;
	
	public ExtendableTrustStrategy(KeyStore extendedTrust) throws NoSuchAlgorithmException, KeyStoreException {
		if(extendedTrust != null) {
			TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			tmf.init(extendedTrust);
			this.trustManagers = tmf.getTrustManagers();
		} else
			this.trustManagers = null;
	}

	@Override
	public boolean isTrusted(X509Certificate[] certs, String authType) throws CertificateException {
		this.capturedCerts = certs;
		if(trustManagers == null)
			return false;
		try {
			for(int i = 0; i < trustManagers.length; ++i) {
				((X509TrustManager) trustManagers[i]).checkServerTrusted(certs, authType);
			}
		} catch(CertificateException e) {
			return false;
		}
		return true;
	}

	public X509Certificate[] getCapturedCerts() {
		return capturedCerts;
	}
}
