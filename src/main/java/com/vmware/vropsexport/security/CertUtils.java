/* 
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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
package com.vmware.vropsexport.security;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStore.LoadStoreParameter;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class CertUtils {
	public static KeyStore loadExtendedTrust(String filename, String password) throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		if(password == null)
			password = "changeit";
		try {
			KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
			InputStream is = new FileInputStream(getActualTruststoreFilename(filename));
			try {
				ks.load(is, password.toCharArray());
				return ks;
			} finally {
				is.close();
			}
		} catch(FileNotFoundException e) {
			return null;
		}
	}

	public static String getThumbprint(X509Certificate cert, boolean space) throws NoSuchAlgorithmException, CertificateEncodingException {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		byte[] der = cert.getEncoded();
		md.update(der);
		byte[] digest = md.digest();
		return hexify(digest, space);
	}
	
	public static void storeCert(X509Certificate cert, String filename, String password) throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
		filename = getActualTruststoreFilename(filename);
		String alias = getThumbprint(cert, false);
		if(password == null)
			password = "changeit";
		KeyStore ks = loadExtendedTrust(filename, password);
		if(ks == null) {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(null, password.toCharArray());
		}
		ks.setCertificateEntry(alias, cert);
		File f = new File(filename);
		File dir = f.getParentFile();
		if(dir !=null && !dir.exists())
			dir.mkdirs();
		OutputStream os = new FileOutputStream(filename);
		try {
			ks.store(os, password.toCharArray());
		} finally {
			os.close();
		}
	}

	private static String getActualTruststoreFilename(String filename) {
		return filename != null 
				? filename : System.getProperty("user.home") + File.separator + ".vropsexport" + File.separator + "truststore";
	}

	private static String hexify (byte bytes[], boolean space) {
		char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', 
				'8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
		StringBuffer buf = new StringBuffer(bytes.length * 2 + (space ? bytes.length - 1 : 0));
		for (int i = 0; i < bytes.length; ++i) {
			if(space && i > 0)
				buf.append(' ');
			buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
			buf.append(hexDigits[bytes[i] & 0x0f]);
		}
		return buf.toString();
	}
}
