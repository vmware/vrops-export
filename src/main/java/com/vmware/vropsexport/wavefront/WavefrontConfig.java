package com.vmware.vropsexport.wavefront;

import com.vmware.vropsexport.Validatable;
import com.vmware.vropsexport.exceptions.ValidationException;

public class WavefrontConfig implements Validatable {
  private String proxyHost;

  private int proxyPort = 2878;

  private String wavefrontURL;

  private String token;

  public String getProxyHost() {
    return proxyHost;
  }

  public void setProxyHost(final String proxyHost) {
    this.proxyHost = proxyHost;
  }

  public int getProxyPort() {
    return proxyPort;
  }

  public void setProxyPort(final int proxyPort) {
    this.proxyPort = proxyPort;
  }

  public String getWavefrontURL() {
    return wavefrontURL;
  }

  public void setWavefrontURL(final String wavefrontURL) {
    this.wavefrontURL = wavefrontURL;
  }

  public String getToken() {
    return token;
  }

  public void setToken(final String token) {
    this.token = token;
  }

  @Override
  public void validate() throws ValidationException {
    if (proxyHost != null) {
      if (wavefrontURL != null) {
        throw new ValidationException("'proxyHost' and 'wavefrontURL' are mutually exclusive");
      }
    } else if (wavefrontURL != null) {
      if (token == null) {
        throw new ValidationException("'token' must be specified");
      }
    } else {
      throw new ValidationException("Either 'proxyHos' or 'wavefrontURL' must be specified");
    }
  }
}
