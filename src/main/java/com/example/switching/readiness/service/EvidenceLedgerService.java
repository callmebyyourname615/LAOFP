package com.example.switching.readiness.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.example.switching.readiness.dto.EvidenceEntry;
import com.example.switching.readiness.dto.EvidenceInput;
import com.example.switching.readiness.dto.LedgerValidation;

@Service
@ConditionalOnProperty(prefix = "switching.readiness", name = "enabled", havingValue = "true")
public class EvidenceLedgerService {
    private static final String GENESIS_HASH = "0".repeat(64);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<EvidenceEntry> entries = new ArrayList<>();

    public EvidenceEntry append(EvidenceInput input) {
        Objects.requireNonNull(input, "input");
        requireText(input.phase(), "phase");
        requireText(input.controlId(), "controlId");
        requireText(input.environment(), "environment");
        requireText(input.gitCommit(), "gitCommit");
        requireText(input.artifactSha256(), "artifactSha256");
        lock.writeLock().lock();
        try {
            long sequence = entries.size() + 1L;
            String previousHash = entries.isEmpty() ? GENESIS_HASH : entries.get(entries.size() - 1).recordHash();
            Instant generatedAt = input.generatedAt() == null ? Instant.now() : input.generatedAt();
            String evidenceId = UUID.randomUUID().toString();
            String hash = hash(canonical(sequence, evidenceId, input, generatedAt, previousHash));
            EvidenceEntry entry = new EvidenceEntry(sequence, evidenceId, input.phase(), input.controlId(),
                    input.status(), input.environment(), input.gitCommit(), input.imageDigest(), generatedAt,
                    input.synthetic(), input.owner(), input.artifactPath(), input.artifactSha256(), previousHash, hash);
            entries.add(entry);
            return entry;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<EvidenceEntry> entries() {
        lock.readLock().lock();
        try { return List.copyOf(entries); }
        finally { lock.readLock().unlock(); }
    }

    public LedgerValidation validate() {
        lock.readLock().lock();
        try {
            List<String> errors = new ArrayList<>();
            String previous = GENESIS_HASH;
            for (EvidenceEntry entry : entries) {
                if (!previous.equals(entry.previousHash())) {
                    errors.add("Broken previousHash at sequence " + entry.sequence());
                }
                EvidenceInput input = new EvidenceInput(entry.phase(), entry.controlId(), entry.status(),
                        entry.environment(), entry.gitCommit(), entry.imageDigest(), entry.generatedAt(),
                        entry.synthetic(), entry.owner(), entry.artifactPath(), entry.artifactSha256());
                String expected = hash(canonical(entry.sequence(), entry.evidenceId(), input, entry.generatedAt(), previous));
                if (!expected.equals(entry.recordHash())) {
                    errors.add("Invalid recordHash at sequence " + entry.sequence());
                }
                previous = entry.recordHash();
            }
            return new LedgerValidation(errors.isEmpty(), entries.size(), previous, List.copyOf(errors));
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String canonical(long sequence, String id, EvidenceInput input, Instant generatedAt, String previousHash) {
        return String.join("|", Long.toString(sequence), id, safe(input.phase()), safe(input.controlId()),
                safe(input.status()), safe(input.environment()), safe(input.gitCommit()), safe(input.imageDigest()),
                generatedAt.toString(), Boolean.toString(input.synthetic()), safe(input.owner()),
                safe(input.artifactPath()), safe(input.artifactSha256()), previousHash);
    }

    private static String safe(Object value) { return value == null ? "" : value.toString().replace("|", "\\|"); }
    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
