# Attach to Vault role: switching-external-secrets
# KV v2 read path for application, migration, trust, backup and restore material.
path "kv/data/switching/prod/*" {
  capabilities = ["read"]
}
path "kv/metadata/switching/prod/*" {
  capabilities = ["read", "list"]
}
