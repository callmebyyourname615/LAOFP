package com.example.switching.webhook.crypto;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "switching.webhook.encryption")
public class WebhookEncryptionProperties {

    /** local for dev/test, vault-transit for staging/prod. */
    private String provider = "local";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private final Vault vault = new Vault();
    private final Local local = new Local();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Vault getVault() {
        return vault;
    }

    public Local getLocal() {
        return local;
    }

    public static class Vault {
        private String address;
        private String token;
        private String namespace;
        private String mount = "transit";
        private String key = "switching-webhook";
        private String authMethod = "token";
        private String kubernetesAuthMount = "kubernetes";
        private String kubernetesRole = "switching-webhook";
        private String serviceAccountTokenFile = "/var/run/secrets/vault/token";
        private Duration tokenRenewalSkew = Duration.ofSeconds(30);

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getMount() {
            return mount;
        }

        public void setMount(String mount) {
            this.mount = mount;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getAuthMethod() {
            return authMethod;
        }

        public void setAuthMethod(String authMethod) {
            this.authMethod = authMethod;
        }

        public String getKubernetesAuthMount() {
            return kubernetesAuthMount;
        }

        public void setKubernetesAuthMount(String kubernetesAuthMount) {
            this.kubernetesAuthMount = kubernetesAuthMount;
        }

        public String getKubernetesRole() {
            return kubernetesRole;
        }

        public void setKubernetesRole(String kubernetesRole) {
            this.kubernetesRole = kubernetesRole;
        }

        public String getServiceAccountTokenFile() {
            return serviceAccountTokenFile;
        }

        public void setServiceAccountTokenFile(String serviceAccountTokenFile) {
            this.serviceAccountTokenFile = serviceAccountTokenFile;
        }

        public Duration getTokenRenewalSkew() {
            return tokenRenewalSkew;
        }

        public void setTokenRenewalSkew(Duration tokenRenewalSkew) {
            this.tokenRenewalSkew = tokenRenewalSkew;
        }
    }

    public static class Local {
        /** Base64 encoded 32-byte AES key; forbidden in the prod profile. */
        private String masterKeyBase64;

        public String getMasterKeyBase64() {
            return masterKeyBase64;
        }

        public void setMasterKeyBase64(String masterKeyBase64) {
            this.masterKeyBase64 = masterKeyBase64;
        }
    }
}
