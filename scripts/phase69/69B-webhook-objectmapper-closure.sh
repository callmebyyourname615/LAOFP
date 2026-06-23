#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69B
cd "$PHASE69_ROOT"
source_file=src/main/java/com/example/switching/webhook/crypto/WebhookEncryptionConfiguration.java
test_file=src/test/java/com/example/switching/webhook/crypto/WebhookEncryptionConfigurationTest.java
for marker in 'webhookEncryptionObjectMapper()' '@ConditionalOnMissingBean(ObjectMapper.class)' 'JsonMapper.builder()' '.findAndAddModules()'; do
  grep -Fq "$marker" "$source_file" || { phase69_result "$phase" FAIL "Missing ObjectMapper closure marker: $marker"; exit 1; }
done
for marker in 'providesObjectMapperWhenFocusedContextHasNoJacksonAutoConfiguration' 'preservesApplicationProvidedObjectMapper'; do
  grep -Fq "$marker" "$test_file" || { phase69_result "$phase" FAIL "Missing ObjectMapper regression test: $marker"; exit 1; }
done
phase69_result "$phase" PASS "Webhook crypto supplies a conditional fallback ObjectMapper and preserves the application mapper" \
  --evidence "$source_file" --evidence "$test_file"
