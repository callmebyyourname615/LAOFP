package com.example.switching.webhook.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.webhook.endpoint-policy")
public class WebhookEndpointPolicyProperties {
    private boolean enabled = true;
    private boolean requireHttps = true;
    private boolean requireAllowlist = false;
    private boolean allowPrivateAddresses = false;
    private Set<Integer> allowedPorts = new LinkedHashSet<>(Set.of(443));
    private List<String> allowedHosts = new ArrayList<>();
    private int dnsResolveTimeoutMillis = 3000;
    private boolean proxyEnabled = false;
    private String proxyHost = "";
    private int proxyPort = 3128;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRequireHttps() { return requireHttps; }
    public void setRequireHttps(boolean requireHttps) { this.requireHttps = requireHttps; }
    public boolean isRequireAllowlist() { return requireAllowlist; }
    public void setRequireAllowlist(boolean requireAllowlist) { this.requireAllowlist = requireAllowlist; }
    public boolean isAllowPrivateAddresses() { return allowPrivateAddresses; }
    public void setAllowPrivateAddresses(boolean allowPrivateAddresses) { this.allowPrivateAddresses = allowPrivateAddresses; }
    public Set<Integer> getAllowedPorts() { return allowedPorts; }
    public void setAllowedPorts(Set<Integer> allowedPorts) { this.allowedPorts = allowedPorts == null ? Set.of() : new LinkedHashSet<>(allowedPorts); }
    public List<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(List<String> allowedHosts) { this.allowedHosts = allowedHosts == null ? List.of() : new ArrayList<>(allowedHosts); }
    public int getDnsResolveTimeoutMillis() { return dnsResolveTimeoutMillis; }
    public void setDnsResolveTimeoutMillis(int dnsResolveTimeoutMillis) { this.dnsResolveTimeoutMillis = dnsResolveTimeoutMillis; }
    public boolean isProxyEnabled() { return proxyEnabled; }
    public void setProxyEnabled(boolean proxyEnabled) { this.proxyEnabled = proxyEnabled; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost == null ? "" : proxyHost.trim(); }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
}
