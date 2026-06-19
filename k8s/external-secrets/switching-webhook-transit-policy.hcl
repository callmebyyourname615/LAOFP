# Attach to Vault role: switching-webhook
# App and migration Job may wrap/unwrap webhook DEKs but cannot manage the key.
path "transit/encrypt/switching-webhook" {
  capabilities = ["update"]
}
path "transit/decrypt/switching-webhook" {
  capabilities = ["update"]
}
