# 09 Config / Hardcode Check Result

Status: PASS_WITH_NOTES

Evidence:
- Java scan found no hardcoded production secrets.
- ArchiveProperties no longer contains hardcoded archive DB username, bucket, or object-storage access key.
- Production profile uses environment variables for DB, Kafka, archive DB, object storage, RTGS, AML, OAuth, mTLS, and signing configuration.
- Scripts mostly use environment placeholders and required-variable guards.
- Local defaults remain in application.yml for developer/runtime convenience.

Notes:
- application.yml still contains localhost/local defaults intended for non-prod.
- OutboxQueueConfig has localhost Kafka fallback, but production profile requires SPRING_KAFKA_BOOTSTRAP_SERVERS.
- Secret-like scan hits such as apiKey/secretKey are mostly field names or config keys, not embedded secret values.

Conclusion:
Configuration is sufficiently externalized for UAT/prod usage, with remaining localhost defaults limited to local/dev fallback paths.
