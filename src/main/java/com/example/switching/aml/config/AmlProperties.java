package com.example.switching.aml.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** AML/CFT runtime configuration. */
@Component
@ConfigurationProperties(prefix = "switching.aml")
public class AmlProperties {

    private boolean screeningEnabled = true;
    private int screeningTimeoutMs = 2000;
    private boolean screeningFailClosed = true;
    private int screeningExecutorCoreSize = 4;
    private int screeningExecutorMaxSize = 16;
    private int screeningExecutorQueueCapacity = 200;
    private String bolFiuUrl = "http://localhost:9099/fiu";
    private String bolFiuApiKey = "dev-fiu-key";
    private int strSubmissionIntervalMinutes = 5;
    private String sanctionsSyncCron = "0 0 2 * * *";
    private final Sanctions sanctions = new Sanctions();

    public boolean isScreeningEnabled() { return screeningEnabled; }
    public void setScreeningEnabled(boolean screeningEnabled) { this.screeningEnabled = screeningEnabled; }

    public int getScreeningTimeoutMs() { return screeningTimeoutMs; }
    public void setScreeningTimeoutMs(int screeningTimeoutMs) { this.screeningTimeoutMs = screeningTimeoutMs; }

    public boolean isScreeningFailClosed() { return screeningFailClosed; }
    public void setScreeningFailClosed(boolean screeningFailClosed) { this.screeningFailClosed = screeningFailClosed; }

    public int getScreeningExecutorCoreSize() { return screeningExecutorCoreSize; }
    public void setScreeningExecutorCoreSize(int value) { this.screeningExecutorCoreSize = value; }

    public int getScreeningExecutorMaxSize() { return screeningExecutorMaxSize; }
    public void setScreeningExecutorMaxSize(int value) { this.screeningExecutorMaxSize = value; }

    public int getScreeningExecutorQueueCapacity() { return screeningExecutorQueueCapacity; }
    public void setScreeningExecutorQueueCapacity(int value) { this.screeningExecutorQueueCapacity = value; }

    public String getBolFiuUrl() { return bolFiuUrl; }
    public void setBolFiuUrl(String bolFiuUrl) { this.bolFiuUrl = bolFiuUrl; }

    public String getBolFiuApiKey() { return bolFiuApiKey; }
    public void setBolFiuApiKey(String bolFiuApiKey) { this.bolFiuApiKey = bolFiuApiKey; }

    public int getStrSubmissionIntervalMinutes() { return strSubmissionIntervalMinutes; }
    public void setStrSubmissionIntervalMinutes(int value) { this.strSubmissionIntervalMinutes = value; }

    public String getSanctionsSyncCron() { return sanctionsSyncCron; }
    public void setSanctionsSyncCron(String sanctionsSyncCron) { this.sanctionsSyncCron = sanctionsSyncCron; }

    public Sanctions getSanctions() { return sanctions; }

    public static class Sanctions {
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(20);
        private int retryAttempts = 3;
        private Duration retryInitialBackoff = Duration.ofSeconds(1);
        private long maxPayloadBytes = 64L * 1024L * 1024L;
        private Duration maximumAge = Duration.ofHours(30);
        private boolean failClosedOnStale = true;
        private final Provider bol = new Provider(false, "", 1);
        private final Provider ofac = new Provider(true,
                "https://sanctionslistservice.ofac.treas.gov/api/PublicationPreview/exports/SDN.XML", 100);
        private final Provider un = new Provider(true,
                "https://scsanctions.un.org/resources/xml/en/consolidated.xml", 100);

        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

        public int getRetryAttempts() { return retryAttempts; }
        public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

        public Duration getRetryInitialBackoff() { return retryInitialBackoff; }
        public void setRetryInitialBackoff(Duration value) { this.retryInitialBackoff = value; }

        public long getMaxPayloadBytes() { return maxPayloadBytes; }
        public void setMaxPayloadBytes(long maxPayloadBytes) { this.maxPayloadBytes = maxPayloadBytes; }

        public Duration getMaximumAge() { return maximumAge; }
        public void setMaximumAge(Duration maximumAge) { this.maximumAge = maximumAge; }

        public boolean isFailClosedOnStale() { return failClosedOnStale; }
        public void setFailClosedOnStale(boolean failClosedOnStale) { this.failClosedOnStale = failClosedOnStale; }

        public Provider getBol() { return bol; }
        public Provider getOfac() { return ofac; }
        public Provider getUn() { return un; }
    }

    public static class Provider {
        private boolean enabled;
        private String url;
        private int minimumRecords;
        private boolean allowInsecureHttp;
        private String apiKeyHeader = "X-API-Key";
        private String apiKey = "";

        public Provider() {
        }

        public Provider(boolean enabled, String url, int minimumRecords) {
            this.enabled = enabled;
            this.url = url;
            this.minimumRecords = minimumRecords;
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getMinimumRecords() { return minimumRecords; }
        public void setMinimumRecords(int minimumRecords) { this.minimumRecords = minimumRecords; }

        public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
        public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }

        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }
}
