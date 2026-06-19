package com.example.switching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "switching.archive")
public class ArchiveProperties {

    private int hotRetentionDays = 90;
    private int partitionForwardDays = 90;
    private boolean workerEnabled = true;
    private boolean partitionMaintenanceEnabled = true;
    private String archiveCron = "0 15 1 * * *";
    private String partitionCron = "0 5 0 * * *";
    private String archiveDbUrl = "jdbc:postgresql://localhost:5434/switching_archive";
    private String archiveDbUsername = "switching_archive";
    private String archiveDbPassword = "switching_archive_postgres_password_change_me";
    private ObjectStorage objectStorage = new ObjectStorage();

    public int getHotRetentionDays() {
        return hotRetentionDays;
    }

    public void setHotRetentionDays(int hotRetentionDays) {
        this.hotRetentionDays = hotRetentionDays;
    }

    public int getPartitionForwardDays() {
        return partitionForwardDays;
    }

    public void setPartitionForwardDays(int partitionForwardDays) {
        this.partitionForwardDays = partitionForwardDays;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public boolean isPartitionMaintenanceEnabled() {
        return partitionMaintenanceEnabled;
    }

    public void setPartitionMaintenanceEnabled(boolean partitionMaintenanceEnabled) {
        this.partitionMaintenanceEnabled = partitionMaintenanceEnabled;
    }

    public String getArchiveCron() {
        return archiveCron;
    }

    public void setArchiveCron(String archiveCron) {
        this.archiveCron = archiveCron;
    }

    public String getPartitionCron() {
        return partitionCron;
    }

    public void setPartitionCron(String partitionCron) {
        this.partitionCron = partitionCron;
    }

    public String getArchiveDbUrl() {
        return archiveDbUrl;
    }

    public void setArchiveDbUrl(String archiveDbUrl) {
        this.archiveDbUrl = archiveDbUrl;
    }

    public String getArchiveDbUsername() {
        return archiveDbUsername;
    }

    public void setArchiveDbUsername(String archiveDbUsername) {
        this.archiveDbUsername = archiveDbUsername;
    }

    public String getArchiveDbPassword() {
        return archiveDbPassword;
    }

    public void setArchiveDbPassword(String archiveDbPassword) {
        this.archiveDbPassword = archiveDbPassword;
    }

    public ObjectStorage getObjectStorage() {
        return objectStorage;
    }

    public void setObjectStorage(ObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    public static class ObjectStorage {
        private String endpoint = "http://localhost:9000";
        private String bucket = "switching-archive";
        private String accessKey = "switching_minio";
        private String secretKey = "switching_minio_password_change_me";
        private int retentionYears = 10;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public int getRetentionYears() {
            return retentionYears;
        }

        public void setRetentionYears(int retentionYears) {
            this.retentionYears = retentionYears;
        }
    }
}
