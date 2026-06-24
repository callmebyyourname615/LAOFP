# Switching — UAT Deployment Starter (Rocky Linux 9.7)

> **Target server:** 8 vCPU / 7.5 GB RAM / 50 GB disk
> **Mode:** Single-node pre-UAT (functional + light integration)
> **Estimated time:** 30–45 minutes from clean OS to first transaction

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
