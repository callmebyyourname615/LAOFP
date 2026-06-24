# Switching — UAT Deployment Starter (Rocky Linux 9.7)

> **Target server:** 8 vCPU / 7.5 GB RAM / 50 GB disk
> **Mode:** Single-node pre-UAT (functional + light integration)
> **Estimated time:** 20–25 minutes for nuke-and-redeploy · 30–45 minutes from clean OS · 10–15 minutes for in-place upgrade

> **Active UAT server:** `root@175.11.0.200:/opt/switching`

---

## 🤖 Chatroom Prompt — paste this whole file into a chat AI to get help

> **Copy everything inside the fenced block below (including the block itself if your AI strips markdown) into a chat window. The AI will then have full context and can guide you turn-by-turn.**

```
ROLE
You are a senior DevOps + payment-systems engineer pair-programming with me
over chat to deploy and operate the "Switching" Lao national payment switch.

PROJECT
- Name: Switching (LaoFPS — Lao Fast Payment Switching)
- Stack: Spring Boot 4 (Java 21) + Postgres 16 + Redpanda (Kafka) + MinIO
- Build: ./mvnw, Maven, Dockerfile, docker-compose.yml at the repo root
- Migrations: Flyway, ~100 contiguous SQL files in src/main/resources/db/migration/
- Test entry points: ./mvnw verify  AND  ./scripts/run_tests.sh
- Latest known-good migration: V107 (grant on `reporting` schema)
- Latest known-good test result on a fresh stack: 0 FAIL / 14 SKIP / 118 PASS

SERVER (the only one I am deploying to)
- SSH: root@175.11.0.200
- Project path: /opt/switching
- OS: Rocky Linux 9.7, kernel 5.14
- CPU: 8 vCPU Intel Xeon Gold 6442Y
- RAM: 7.5 GiB (5.6 GiB free)  ← LOW; tune JVM and Postgres accordingly
- Swap: 5 GiB
- Disk: 50 GB total, 44 GB root, ~35 GB free  ← TIGHT; need log rotation
- Already has an older Switching deploy running via docker compose

CAPABILITY ENVELOPE OF THIS SERVER
- CAN do: functional UAT, security/compliance tests, smoke + ≤ 500 TPS,
  Phase pipeline preflights, bank-integration sandbox.
- CANNOT do: 10K TPS sustained, 20K burst, 8h soak, real multi-region DR,
  real HSM, BRD ACC-026 full performance certification.
- For full UAT cert I will need 4 nodes / 40 vCPU / 96 GB / 2 TB later.

USER PROFILE
- Hands-on builder, Thai speaker, comfortable in zsh/bash.
- Wants compact, code-first answers. Skip beginner DB/Docker explanations.
- HARD RULE: do not change DB credential values (POSTGRES_*, DB_APP_*,
  FLYWAY_*, ARCHIVE_*, MINIO_ROOT_*). Generating NEW passwords on a fresh
  install is fine; rewriting existing ones is not.

GOAL OF THIS SESSION
Pick ONE of three paths based on what I tell you next:
  (A) Nuke-and-redeploy on the same server  → §🧨 (N.0–N.10)  ~20 min
  (B) In-place upgrade keeping existing data → §🔁 (U.1–U.9)  ~15 min
  (C) Fresh OS install from a clean Rocky 9 → §0–§13         ~40 min

OPERATING RULES
1. Before any destructive step (down -v, rm, mv, prune) STOP and confirm
   with me. Mention exactly which paths/volumes will be lost.
2. After every step, give me a single command I can paste, plus the
   expected output snippet so I can verify before moving on.
3. If a command fails, ask me for the actual error text — do not guess.
4. Never skip the backup step (N.1 / U.1). If I try to skip, push back.
5. When the run_tests.sh smoke ends, summarize PASS/FAIL/SKIP counts only.

WHAT TO DO RIGHT NOW
Ask me which of (A) (B) (C) I want, then walk me through that path one
step at a time, waiting for my "ok" before moving on.
```

> After pasting, the next thing you say should be one of: `A`, `B`, or `C`.
> Everything below is the source-of-truth runbook the AI will follow.

---

## 🧨 Nuke-and-redeploy (full clean wipe)  — use this when you want to throw away the old project entirely and start fresh on the same box

> ⚠️ **DESTRUCTIVE.** This deletes ALL Postgres data, Kafka/Redpanda logs, MinIO objects, and the old project directory. Do U.1 backup BEFORE running anything below if there is any state you might need later (test data, audit chain, idempotency keys).

### N.0 SSH in + go to the install dir

```bash
ssh root@175.11.0.200
cd /opt/switching      # adjust if your path differs
docker compose ps      # confirm this is the deployment you want to wipe
```

### N.1 Optional final backup (1 min) — skip only if you truly don't care

```bash
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p /root/switching-final-backup-$TS
docker compose exec -T postgres pg_dumpall -U "${POSTGRES_USER:-switching}" \
  | gzip > /root/switching-final-backup-$TS/postgres.sql.gz
cp .env /root/switching-final-backup-$TS/.env
ls -lh /root/switching-final-backup-$TS/
```

### N.2 Stop and tear down the stack (1 min)

```bash
# Stop everything cleanly first (lets containers flush)
docker compose down --timeout 30

# Now remove containers + volumes + named volumes (ALL DATA GONE)
docker compose down -v --remove-orphans

# Sanity-check no Switching containers/volumes remain
docker ps -a --filter "name=switching" -q | xargs -r docker rm -f
docker volume ls --filter "name=switching" -q | xargs -r docker volume rm -f
docker network ls --filter "name=switching" -q | xargs -r docker network rm
```

### N.3 Remove the old project directory (30 sec)

```bash
cd /opt
# Make sure you're really deleting the right path
ls -la /opt/switching | head -3

# Move first (safer than rm -rf) — you can purge later if redeploy works
mv /opt/switching /opt/switching.OLD.$(date +%Y%m%d-%H%M%S)

# Verify it's gone
ls -la /opt/ | grep switching
```

### N.4 Free up Docker image + build cache (1 min)

```bash
# Drop the old app image so the next build can't accidentally use it
docker images --filter "reference=*switching*" -q | xargs -r docker rmi -f
docker images --filter "dangling=true" -q | xargs -r docker rmi -f
docker builder prune -af
docker system prune -af --volumes

# Confirm disk reclaimed
df -h /
docker system df
```

### N.5 Fresh-clone the new code (1 min)

```bash
cd /opt
# Pick ONE of the following:

# (a) clone from GitHub
git clone https://github.com/<your-org>/switching.git
# or with SSH key on the server:
# git clone git@github.com:<your-org>/switching.git

# (b) OR scp-in a fresh tarball from your laptop (when server has no GitHub access)
# from your Mac:
#   tar czf /tmp/switching.tgz --exclude=.git --exclude=target -C ~/Desktop Switching
#   scp /tmp/switching.tgz root@175.11.0.200:/opt/
# on server:
#   tar xzf /tmp/switching.tgz && mv Switching switching

cd /opt/switching
git log --oneline -3            # confirm you're on the commit you intended
```

### N.6 Restore (or regenerate) `.env` (3 min)

```bash
# Option A — restore the .env you backed up in N.1 (keeps the same secrets)
cp /root/switching-final-backup-*/.env /opt/switching/.env

# Option B — generate brand-new secrets (best for a true clean install)
cp .env.example .env
for v in POSTGRES REPLICATION DB_APP FLYWAY ARCHIVE_POSTGRES MINIO_ROOT; do
  pw=$(openssl rand -base64 32 | tr -d '/+=' | head -c 24)
  sed -i "s|^${v}_PASSWORD=.*|${v}_PASSWORD=${pw}|" .env
done
echo "MESSAGE_CRYPTO_KEY_BASE64=$(openssl rand -base64 32)" >> .env
echo "SWITCHING_WEBHOOK_ENCRYPTION_LOCAL_MASTER_KEY_BASE64=$(openssl rand -base64 32)" >> .env
sed -i -E 's/^(SETTLEMENT_CYCLE[1-4]_CRON)="([^"]+)"$/\1=\2/' .env

# Either way — add the rate-limit overrides so run_tests.sh runs clean
cat >> .env <<'EOF'
RATE_LIMIT_RPM=10000
RATE_LIMIT_ENABLED=false
EOF
```

### N.7 Drop in the low-RAM override (2 min)

Copy the canonical `docker-compose.override.yml` from §5 into `/opt/switching/`.
Without it the JVM `-Xmx` defaults will OOM on a 7.5 GB box.

### N.8 First build + start (10 min)

```bash
# Build the app image fresh (no cache from the old project)
docker compose build --no-cache app

# Start infra first
docker compose up -d postgres minio minio-init redpanda

# Wait for postgres healthy
until docker compose exec -T postgres pg_isready -U switching > /dev/null 2>&1; do
  echo "waiting for postgres..."; sleep 3
done

# Start app (Flyway will create all 100+ migrations on a clean DB)
docker compose up -d app
docker compose logs -f app | grep -E "Migrating|Started Switching|ERROR|Caused by"
# Ctrl-C when you see "Started SwitchingApplication"
```

### N.9 Verify (2 min)

```bash
# Health
curl -sf http://localhost:8080/actuator/health | jq .

# Flyway head (should be V107 or higher on the new code)
docker compose exec -T postgres psql -U switching_app -d switching_db \
  -c "SELECT version, installed_on FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 5"

# Full E2E smoke
./scripts/run_tests.sh --skip-rate-limit | tail -15
# expected: PASS ~118 / FAIL 0 / SKIP ~14
```

### N.10 Cleanup the moved-aside old directory (optional, do after smoke green)

```bash
# Only after you confirm the new deploy is green
rm -rf /opt/switching.OLD.*
# Backup tarball stays in /root/switching-final-backup-* until you remove it
ls -lh /root/switching-final-backup-*/
```

### Nuke Definition of Done

- [ ] `docker compose ps` shows fresh containers (recent `Created` timestamps)
- [ ] `flyway_schema_history` head matches the new commit's latest migration
- [ ] `/actuator/health` returns `UP`
- [ ] `run_tests.sh --skip-rate-limit` → **0 FAIL**
- [ ] `/opt/switching.OLD.*` removed (or moved off-box)
- [ ] Disk usage < 60% (`df -h /`)

### Rollback for the nuke path

The nuke path is **destructive by design** — there is no in-place rollback. If something goes wrong:

1. Stop the new stack: `docker compose down -v`
2. If you took the N.1 backup, restore by:
   - `mv /opt/switching.OLD.* /opt/switching` (put the code back)
   - `cd /opt/switching && docker compose up -d postgres`
   - `gunzip -c /root/switching-final-backup-*/postgres.sql.gz | docker compose exec -T postgres psql -U switching`
   - `docker compose up -d app`
3. If you didn't take a backup — you're starting fresh again from N.5.

That's why **N.1 is not optional in practice**, only in name.

---

## 🔁 If the server ALREADY has an older Switching deployment — and you want to keep the data

Use this **upgrade path** (15 min) instead of the full install in §1–§7.
For a clean-server install, skip to §0. For a destructive wipe + redeploy on the same box, use the Nuke-and-redeploy path above.

### U.1 Snapshot current state (3 min — DO NOT SKIP)

```bash
cd ~/work/switching   # or wherever your clone lives

# 1) Identify what's running
docker compose ps
docker compose images app    # note the current image tag/SHA
git log --oneline -1         # note the current commit

# 2) Backup .env and override file
cp .env .env.backup.$(date +%Y%m%d-%H%M%S)
[ -f docker-compose.override.yml ] && \
  cp docker-compose.override.yml docker-compose.override.yml.backup.$(date +%Y%m%d-%H%M%S)

# 3) Backup Postgres data (volume snapshot)
docker compose exec -T postgres pg_dumpall -U "${POSTGRES_USER:-switching}" \
  | gzip > ~/switching-backup-$(date +%Y%m%d-%H%M%S).sql.gz
ls -lh ~/switching-backup-*.sql.gz | tail -1

# 4) Capture current Flyway head (so you know what schema you're upgrading from)
docker compose exec -T postgres psql -U switching_app -d switching_db \
  -c "SELECT version, installed_on FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"
```

**Stop here if step 3 fails.** Fix the backup before touching anything else.

### U.2 Pull new code (1 min)

```bash
# Stash any local edits you made on the server (e.g. test scripts, override file)
git status --short
git stash push -m "uat-server-local-$(date +%Y%m%d)"   # only if there are local changes

# Pull
git fetch --all --tags
git log --oneline HEAD..origin/main | head -20         # preview what's coming in
git pull --ff-only origin main

# Confirm new HEAD
git log --oneline -3
```

**If `git pull` rejects with non-fast-forward**, you have local commits — review them and rebase or reset to origin/main intentionally.

### U.3 Reconcile your `.env` with new template (2 min)

The new code may have added env vars (e.g. `RATE_LIMIT_RPM`, Phase II flags, OpenTelemetry endpoints).

```bash
# Show new keys the template added that your .env lacks
comm -23 \
  <(grep -E '^[A-Z_]+=' .env.example | cut -d= -f1 | sort -u) \
  <(grep -E '^[A-Z_]+=' .env         | cut -d= -f1 | sort -u)

# Append each missing key with its example default, then edit values:
#   nano .env
```

Common keys added recently — add these if missing:

```bash
cat >> .env <<'EOF'
# Added by upgrade reconciliation (review then edit)
RATE_LIMIT_RPM=10000
RATE_LIMIT_ENABLED=true
SWITCHING_PHASE_II_ENABLED=false
PHASE_II_RTP_ENABLED=false
PHASE_II_PROMOTION_ENABLED=false
PHASE_II_PUSH_ORCHESTRATOR_ENABLED=false
EOF
```

### U.4 Update `docker-compose.override.yml` (1 min)

If you carry a low-RAM override (see §5 below for the canonical version), confirm it still aligns with the new `docker-compose.yml` service names. If a service was added (e.g. `pgbouncer`, `postgres-exporter`), decide whether to enable it on this box.

```bash
# Quick diff vs the canonical override in §5
cat docker-compose.override.yml
```

### U.5 Rebuild + roll the app only (5 min)

DB and other services keep running; only the Spring Boot image gets a fresh build.

```bash
# Rebuild app image (uses BuildKit cache where possible)
docker compose build app

# Bring the app down, then back up — Flyway migrations apply on startup
docker compose up -d --force-recreate --no-deps app

# Tail logs until you see:
#   - "Migrating schema ..."  (if new migrations)
#   - "Started SwitchingApplication"
docker compose logs -f app | grep -E "Migrating|Started Switching|ERROR|Caused by"
# Ctrl-C when it's up
```

### U.6 Verify migration head (1 min)

```bash
# Should now show the new latest migration (e.g. V106 or higher)
docker compose exec -T postgres psql -U switching_app -d switching_db \
  -c "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 3"
```

### U.7 Smoke test (2 min)

```bash
curl -s http://localhost:8080/actuator/health | jq .
# expect: {"status":"UP",...}

# Optional fuller test (warn: hits rate limit unless --skip-rate-limit + RATE_LIMIT_ENABLED=false)
./scripts/run_tests.sh --skip-rate-limit | tail -10
```

### U.8 Rollback procedure (if anything is wrong)

```bash
# 1) Roll the app back to the previous commit
git log --oneline -5
git checkout <previous-good-sha>
docker compose build app
docker compose up -d --force-recreate --no-deps app

# 2) If the DB needs to come back too (rare — Flyway is forward-only by design)
docker compose down
docker compose up -d postgres
gunzip -c ~/switching-backup-<timestamp>.sql.gz | \
  docker compose exec -T postgres psql -U "${POSTGRES_USER:-switching}"
docker compose up -d app
```

### U.9 Cleanup (optional, do weekly)

```bash
# Remove dangling images from the rebuild
docker image prune -f

# Trim stopped containers, networks, build cache
docker system prune -af --filter "until=24h"

# Show disk pressure
df -h /
docker system df
```

### Upgrade Definition of Done

- [ ] Backup file `~/switching-backup-*.sql.gz` exists
- [ ] `git log -1` shows the new commit you intended
- [ ] App container is `Up` and `/actuator/health` returns `UP`
- [ ] `flyway_schema_history` head matches the expected new version
- [ ] Smoke test passes (or known failures are documented)

---

## 0. Pre-flight checklist (5 min)

```bash
# Confirm spec
nproc                                 # should be ≥ 8
free -g                               # should show ≥ 7 GB
df -h /                               # should show ≥ 35 GB free
cat /etc/os-release | head -3         # Rocky Linux 9.x
```

If any value is below threshold, **stop and provision a bigger box**.

---

## 1. Install Docker + git + tools (5 min)

```bash
# Update
sudo dnf -y update

# Repo + Docker CE
sudo dnf install -y dnf-plugins-core
sudo dnf config-manager --add-repo https://download.docker.com/linux/rhel/docker-ce.repo
sudo dnf install -y \
  docker-ce docker-ce-cli containerd.io docker-compose-plugin \
  git jq curl tmux htop iotop

# Enable + start
sudo systemctl enable --now docker

# Run docker without sudo (re-login after)
sudo usermod -aG docker "$USER"
newgrp docker

# Verify
docker version
docker compose version
```

---

## 2. Tune kernel + Docker for low-RAM box (5 min)

```bash
# Increase vm.max_map_count (Postgres + Kafka need it)
echo "vm.max_map_count=262144" | sudo tee /etc/sysctl.d/99-switching.conf
echo "vm.swappiness=10" | sudo tee -a /etc/sysctl.d/99-switching.conf
sudo sysctl -p /etc/sysctl.d/99-switching.conf

# Open firewall for app + ops (UAT only — tighten for prod)
sudo firewall-cmd --permanent --add-port=8080/tcp     # app
sudo firewall-cmd --permanent --add-port=5433/tcp     # postgres (debug)
sudo firewall-cmd --reload

# Docker daemon: cap log size so disk doesn't fill up
sudo mkdir -p /etc/docker
cat <<'EOF' | sudo tee /etc/docker/daemon.json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "3" },
  "default-ulimits": { "nofile": { "Name": "nofile", "Hard": 65536, "Soft": 65536 } }
}
EOF
sudo systemctl restart docker
```

---

## 3. Clone repo (1 min)

```bash
# SSH key if needed
ssh-keygen -t ed25519 -C "uat-server" -f ~/.ssh/id_ed25519 -N ""
cat ~/.ssh/id_ed25519.pub   # add to GitHub

# Clone
mkdir -p ~/work && cd ~/work
git clone git@github.com:<your-org>/switching.git
cd switching
```

---

## 4. Prepare environment file (5 min)

```bash
# Copy template
cp .env.example .env

# For UAT, generate fresh dev passwords (NOT the same as prod):
for v in POSTGRES REPLICATION DB_APP FLYWAY ARCHIVE_POSTGRES MINIO_ROOT; do
  pw=$(openssl rand -base64 32 | tr -d '/+=' | head -c 24)
  sed -i "s|^${v}_PASSWORD=.*|${v}_PASSWORD=${pw}|" .env
done

# Crypto keys
echo "MESSAGE_CRYPTO_KEY_BASE64=$(openssl rand -base64 32)" >> .env
echo "SWITCHING_WEBHOOK_ENCRYPTION_LOCAL_MASTER_KEY_BASE64=$(openssl rand -base64 32)" >> .env

# Strip literal quotes from cron entries (known bug)
sed -i -E 's/^(SETTLEMENT_CYCLE[1-4]_CRON)="([^"]+)"$/\1=\2/' .env

# Verify
grep -cE "^(POSTGRES|REPLICATION|DB_APP|FLYWAY|ARCHIVE_POSTGRES|MINIO_ROOT)_PASSWORD=" .env
# expected: 6
```

---

## 5. Create override for low-RAM tuning (2 min)

```bash
cat <<'EOF' > docker-compose.override.yml
services:
  postgres:
    command: >
      postgres
      -c shared_buffers=256MB
      -c effective_cache_size=512MB
      -c work_mem=8MB
      -c max_connections=50
      -c max_wal_size=512MB
      -c log_min_duration_statement=1000
    deploy:
      resources:
        limits: { memory: 1G, cpus: '2' }
        reservations: { memory: 512M }

  postgres-read-replica:
    deploy:
      resources:
        limits: { memory: 512M }
    profiles: [full]      # skip in minimal mode

  postgres-archive:
    deploy:
      resources:
        limits: { memory: 384M }
    profiles: [full]      # skip in minimal mode

  redpanda:
    command:
      - redpanda
      - start
      - --smp=1
      - --memory=512M
      - --reserve-memory=0M
      - --overprovisioned
      - --node-id=0
      - --check=false
      - --kafka-addr=PLAINTEXT://0.0.0.0:9092
      - --advertise-kafka-addr=PLAINTEXT://redpanda:9092
    deploy:
      resources: { limits: { memory: 768M } }

  minio:
    deploy:
      resources: { limits: { memory: 384M } }

  app:
    environment:
      JAVA_OPTS: >
        -Xms512m -Xmx1500m
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:+ExitOnOutOfMemoryError
      HIKARI_MAX_POOL_SIZE: "10"
      HIKARI_MIN_IDLE: "2"
    deploy:
      resources:
        limits: { memory: 2G, cpus: '4' }
        reservations: { memory: 768M }
EOF
```

---

## 6. Build + start minimal stack (10 min first build, 1 min subsequent)

```bash
# Build app image
docker compose build app

# Start essentials only (skip backup/replica/archive)
docker compose up -d postgres minio minio-init redpanda

# Wait for healthy
docker compose ps
docker compose logs -f postgres
# Ctrl-C when you see "database system is ready to accept connections"

# Start app
docker compose up -d app

# Tail app startup
docker compose logs -f app | grep -E "Started|ERROR|Caused by"
# Ctrl-C when you see "Started SwitchingApplication"
```

---

## 7. Smoke test (3 min)

```bash
# Health
curl -s http://localhost:8080/actuator/health | jq .
# expect: {"status":"UP",...}

# Run the full E2E test runner
./scripts/run_tests.sh --wait --timeout 120 | tee /tmp/run-tests.log

# Expected summary at end:
# Passed: NN | Failed: 0 | Skipped: NN
```

---

## 8. Run readiness gate (5 min)

```bash
./scripts/execute-and-verify/00-run-all.sh
# Steps 01-02 should pass; 03 may take 30+ min on this box
# Step 04 will warn about placeholder .env.prod.example — fine for UAT
# Step 05 will list missing evidence — expected
```

---

## 9. Day-to-day operations

```bash
# Tail app
docker compose logs -f app

# Restart after .env change
docker compose down && docker compose up -d

# Run one specific integration test
docker compose exec app sh -c "./mvnw -q test -Dtest=RtpRequestIntegrationTest"

# Reset everything (deletes data!)
docker compose down -v && docker compose up -d

# Clean stale Docker artifacts (do weekly)
docker system prune -af --filter "until=72h"
```

---

## 10. Disk hygiene (set this up day 1)

```bash
# Cron: prune old Docker images + Postgres logs nightly at 03:00
( crontab -l 2>/dev/null; cat <<'CRON'
0 3 * * * docker system prune -af --filter "until=24h" >/dev/null 2>&1
15 3 * * * find /var/lib/docker/containers -name "*-json.log" -size +200M -delete
CRON
) | crontab -

# Check disk weekly
df -h /
docker system df
```

---

## 11. What to do when you hit a wall

| Symptom | Likely cause | Fix |
|---|---|---|
| App container restarts loop | OOM-killed | Reduce JVM `-Xmx` further |
| `connection refused` on 5433 | Postgres not started | `docker compose logs postgres` |
| Migration fails on V83/V84 | Existing data | `docker compose down -v` to reset |
| `disk full` | Logs accumulated | step 10 cron + `docker system prune` |
| Tests timeout | Box CPU pegged | Stop `docker compose stop redpanda` if not needed |

---

## 12. Definition of done — "UAT smoke green"

- [ ] `curl /actuator/health` returns `UP`
- [ ] `scripts/run_tests.sh --wait` finishes with **Failed: 0**
- [ ] `scripts/execute-and-verify/00-run-all.sh` steps 01–02 pass
- [ ] App reachable from external IP on `:8080` (firewall correct)
- [ ] Logs are rotating (no file > 200 MB in `/var/lib/docker/containers/`)
- [ ] Disk usage < 80%

---

## 13. What this server CANNOT do (acknowledge upfront)

- ❌ 10K TPS sustained or 20K burst (out of RAM)
- ❌ 8h soak (disk fills)
- ❌ Multi-region DR
- ❌ Real HSM
- ❌ Phase 54 full BRD certification

For those, you need at least 4 nodes / 96 GB / 2 TB (see [docs/UAT_RESOURCE_SPEC.md](UAT_RESOURCE_SPEC.md) if it exists).

---

*Single-source-of-truth deployment starter. Update inline as steps evolve.*
