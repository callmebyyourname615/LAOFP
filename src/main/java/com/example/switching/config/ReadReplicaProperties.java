package com.example.switching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.read-replica")
public class ReadReplicaProperties {
    private boolean enabled = true;
    private String url;
    private String username;
    private String password;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isConfigured() {
        return enabled && url != null && !url.isBlank();
    }
}
