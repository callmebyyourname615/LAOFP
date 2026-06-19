FROM postgres:16-bookworm

ARG TARGETARCH

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       age \
       awscli \
       ca-certificates \
       curl \
       jq \
       openssl \
       tini \
       util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && install -d -o postgres -g postgres -m 0750 \
       /opt/switching-backup/bin \
       /opt/switching-backup/config \
       /var/lib/switching-backup/incoming \
       /var/lib/switching-backup/work \
       /var/lib/switching-backup/metrics

COPY --chown=postgres:postgres bin/ /opt/switching-backup/bin/
COPY --chown=postgres:postgres config/ /opt/switching-backup/config/

RUN chmod 0550 /opt/switching-backup/bin/*.sh \
    && chmod 0440 /opt/switching-backup/config/*

ENV PATH="/opt/switching-backup/bin:${PATH}" \
    BACKUP_WORK_DIR="/var/lib/switching-backup/work" \
    WAL_SPOOL_DIR="/var/lib/switching-backup/incoming" \
    BACKUP_METRICS_DIR="/var/lib/switching-backup/metrics"

USER postgres
WORKDIR /var/lib/switching-backup
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["full-backup.sh"]
