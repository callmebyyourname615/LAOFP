package com.example.switching.maintenance.service;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SchedulerLockService {

    private final JdbcTemplate jdbcTemplate;
    private final String lockedBy;

    public SchedulerLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockedBy = resolveLockedBy();
    }

    public boolean acquire(String lockName, int lockMinutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lockUntil = now.plusMinutes(lockMinutes);
        List<Integer> rows = jdbcTemplate.queryForList("""
                INSERT INTO scheduler_locks (lock_name, lock_until, locked_at, locked_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (lock_name) DO UPDATE
                SET lock_until = EXCLUDED.lock_until,
                    locked_at = EXCLUDED.locked_at,
                    locked_by = EXCLUDED.locked_by
                WHERE scheduler_locks.lock_until < EXCLUDED.locked_at
                RETURNING 1
                """, Integer.class, lockName, lockUntil, now, lockedBy);
        return !rows.isEmpty();
    }

    public void release(String lockName) {
        jdbcTemplate.update("""
                UPDATE scheduler_locks
                SET lock_until = ?, locked_at = ?, locked_by = ?
                WHERE lock_name = ? AND locked_by = ?
                """, LocalDateTime.now(), LocalDateTime.now(), lockedBy, lockName, lockedBy);
    }

    private static String resolveLockedBy() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "switching-app";
        }
    }
}
