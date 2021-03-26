package com.vmware.vropsexport.wavefront;

public class WavefrontConfig {
  private String proxyHost;

  private int proxyPort;

  private String wavefrontURL;

  private String token;

  public String getProxyHost() {
    return proxyHost;
  }

  public void setProxyHost(String proxyHost) {
    this.proxyHost = proxyHost;
  }

  public int getProxyPort() {
    return proxyPort;
  }

  public void setProxyPort(int proxyPort) {
    this.proxyPort = proxyPort;
  }

  public String getWavefrontURL() {
    return wavefrontURL;
  }

  public void setWavefrontURL(String wavefrontURL) {
    this.wavefrontURL = wavefrontURL;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }
}
