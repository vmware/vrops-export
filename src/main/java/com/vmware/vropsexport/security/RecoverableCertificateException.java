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
package com.vmware.vropsexport.security;

import com.vmware.vropsexport.exceptions.ExporterException;
import java.security.cert.X509Certificate;

public class RecoverableCertificateException extends ExporterException {
  private static final long serialVersionUID = 6702660675940528780L;
  private final X509Certificate[] capturedCerts;

  public RecoverableCertificateException(
      final X509Certificate[] capturedCerts, final Throwable cause) {
    super(cause);
    this.capturedCerts = capturedCerts;
  }

  public X509Certificate[] getCapturedCerts() {
    return capturedCerts;
  }
}
