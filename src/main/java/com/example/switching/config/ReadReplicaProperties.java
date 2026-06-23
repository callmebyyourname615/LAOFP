package com.example.switching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.read-replica")
public class ReadReplicaProperties {
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private int maximumPoolSize = 20;
    private int minimumIdle = 2;
    private long connectionTimeoutMs = 3000;
    private long validationTimeoutMs = 1000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }
    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }
    public long getValidationTimeoutMs() { return validationTimeoutMs; }
    public void setValidationTimeoutMs(long validationTimeoutMs) { this.validationTimeoutMs = validationTimeoutMs; }

    public boolean isConfigured() {
        return enabled && url != null && !url.isBlank();
    }

    public void validate() {
        if (!enabled) return;
        if (url == null || url.isBlank() || username == null || username.isBlank()) {
            throw new IllegalStateException("Read replica is enabled but URL/username is not configured");
        }
        if (maximumPoolSize < 1 || minimumIdle < 0 || minimumIdle > maximumPoolSize) {
            throw new IllegalStateException("Invalid read-replica Hikari pool sizing");
        }
        if (connectionTimeoutMs < 250 || validationTimeoutMs < 250) {
            throw new IllegalStateException("Read-replica timeouts must be at least 250 ms");
        }
    }
}
