# 66G — DR Runtime Certification

Fault injection requires the destructive confirmation string. Region failover remains optional unless multi-region is in scope.

## Evidence

Store runtime artifacts under `build/phase66-evidence/<run-id>/66G/`. Redact secrets before signing.

## Database failover

Supply `DB_FAILOVER_COMMAND` and `DB_RECOVERY_CHECK_COMMAND`. Both are executed only after the UAT destructive confirmation gate.
