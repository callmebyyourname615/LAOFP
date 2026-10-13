#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
OUTPUT_DIR="${OUTPUT_DIR:-runtime-evidence/phase99-risk-rescore-${STAMP}}"
PHASE98_EVIDENCE_DIR="${PHASE98_EVIDENCE_DIR:-}"
RUN_PHASE98="${RUN_PHASE98:-false}"

mkdir -p "$OUTPUT_DIR"

for command in jq sha256sum; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 64; }
done

if [[ "$RUN_PHASE98" == "true" ]]; then
  PHASE98_EVIDENCE_DIR="${PHASE98_EVIDENCE_DIR:-runtime-evidence/phase98-production-runtime-${STAMP}}"
  set +e
  EVIDENCE_DIR="$PHASE98_EVIDENCE_DIR" scripts/phase98/98-production-runtime-evidence-closure.sh \
    > "$OUTPUT_DIR/phase98-execution.log" 2>&1
  phase98_rc=$?
  set -e
  echo "$phase98_rc" > "$OUTPUT_DIR/phase98-exit-code.txt"
fi

if [[ -z "$PHASE98_EVIDENCE_DIR" ]]; then
  PHASE98_EVIDENCE_DIR="$(find runtime-evidence -maxdepth 1 -type d -name 'phase98-production-runtime-*' | sort | tail -1)"
fi

if [[ -z "$PHASE98_EVIDENCE_DIR" || ! -f "$PHASE98_EVIDENCE_DIR/score.json" || ! -f "$PHASE98_EVIDENCE_DIR/checks.jsonl" ]]; then
  echo "Missing Phase 98 evidence. Set PHASE98_EVIDENCE_DIR or RUN_PHASE98=true." >&2
  exit 66
fi
export PHASE98_EVIDENCE_DIR

cp "$PHASE98_EVIDENCE_DIR/score.json" "$OUTPUT_DIR/phase98-score.json"
cp "$PHASE98_EVIDENCE_DIR/checks.jsonl" "$OUTPUT_DIR/phase98-checks.jsonl"
[[ -f "$PHASE98_EVIDENCE_DIR/SUMMARY.md" ]] && cp "$PHASE98_EVIDENCE_DIR/SUMMARY.md" "$OUTPUT_DIR/phase98-summary.md"

jq -s '
  def count($s): map(select(.status == $s)) | length;
  def ids($s): map(select(.status == $s) | .id);
  . as $checks
  | {
      schemaVersion: 1,
      phase: "99",
      sourceEvidenceDir: env.PHASE98_EVIDENCE_DIR,
      generatedAt: (now | todateiso8601),
      runtime: {
        totalChecks: length,
        passed: count("PASS"),
        warnings: count("WARN"),
        failed: count("FAIL"),
        blocked: count("BLOCKED"),
        notRun: count("NOT_RUN"),
        failedChecks: ids("FAIL"),
        blockedChecks: ids("BLOCKED"),
        warningChecks: ids("WARN"),
        notRunChecks: ids("NOT_RUN")
      }
    }
  | .runtime.scorePercent = (
      if .runtime.totalChecks == 0 then 0
      else ((.runtime.passed * 100) / .runtime.totalChecks | floor)
      end
    )
  | .goLiveDecision = (
      if .runtime.failed == 0 and .runtime.blocked == 0 and .runtime.warnings == 0 and .runtime.notRun == 0
      then "GO_CANDIDATE"
      else "NO_GO"
      end
    )
  | .productionReadyPercent = (
      if .goLiveDecision == "GO_CANDIDATE" then 90
      elif .runtime.failed > 0 or .runtime.blocked > 0 then 78
      elif .runtime.warnings > 0 or .runtime.notRun > 0 then 84
      else 88
      end
    )
  | .riskBand = (
      if .productionReadyPercent >= 90 then "LOW"
      elif .productionReadyPercent >= 84 then "MEDIUM"
      else "HIGH"
      end
    )
' "$PHASE98_EVIDENCE_DIR/checks.jsonl" > "$OUTPUT_DIR/risk-rescore.json"

{
  echo "# Phase 99 Runtime Evidence Execution + Risk Re-score"
  echo
  echo "Status: closed"
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Source evidence: $PHASE98_EVIDENCE_DIR"
  echo
  echo "## Decision"
  echo
  jq -r '"- Go-live decision: `\(.goLiveDecision)`\n- Production ready percent: `\(.productionReadyPercent)%`\n- Risk band: `\(.riskBand)`\n- Runtime score: `\(.runtime.scorePercent)%`"' "$OUTPUT_DIR/risk-rescore.json"
  echo
  echo "## Runtime Counts"
  echo
  jq -r '"- PASS: \(.runtime.passed)\n- WARN: \(.runtime.warnings)\n- FAIL: \(.runtime.failed)\n- BLOCKED: \(.runtime.blocked)\n- NOT_RUN: \(.runtime.notRun)"' "$OUTPUT_DIR/risk-rescore.json"
  echo
  echo "## Open Risk"
  echo
  jq -r '
    if (.runtime.failedChecks | length) > 0 then
      "- Failed checks: `" + (.runtime.failedChecks | join("`, `")) + "`"
    else "- Failed checks: none" end,
    if (.runtime.blockedChecks | length) > 0 then
      "- Blocked checks: `" + (.runtime.blockedChecks | join("`, `")) + "`"
    else "- Blocked checks: none" end,
    if (.runtime.warningChecks | length) > 0 then
      "- Warning checks: `" + (.runtime.warningChecks | join("`, `")) + "`"
    else "- Warning checks: none" end,
    if (.runtime.notRunChecks | length) > 0 then
      "- Not-run checks: `" + (.runtime.notRunChecks | join("`, `")) + "`"
    else "- Not-run checks: none" end
  ' "$OUTPUT_DIR/risk-rescore.json"
  echo
  echo "## Source Checks"
  echo
  jq -s -r '"| Check | Status | Detail |\n| --- | --- | --- |", (.[] | "| \(.id) | \(.status) | \(.detail | gsub("\\|"; "/")) |")' "$PHASE98_EVIDENCE_DIR/checks.jsonl"
} > "$OUTPUT_DIR/SUMMARY.md"

cat > "$OUTPUT_DIR/PHASE99_CLOSURE.md" <<MD
# Phase 99 Closure

Source evidence: \`$PHASE98_EVIDENCE_DIR\`

Risk re-score output:

- \`risk-rescore.json\`
- \`SUMMARY.md\`
- \`SHA256SUMS\`

Go-live decision is machine-derived from Phase 98 runtime checks. Any FAIL, BLOCKED, WARN, or NOT_RUN keeps decision at \`NO_GO\`.
MD

find "$OUTPUT_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$OUTPUT_DIR/SHA256SUMS"

jq . "$OUTPUT_DIR/risk-rescore.json"
echo "Phase 99 output: $OUTPUT_DIR"
