package com.example.switching.paymentorchestration;

import java.sql.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class PushPaymentPolicyRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public PushPaymentPolicyRepository(
            JdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public PushPaymentPolicy active(PaymentChannel channel) {
        List<PushPaymentPolicy> found = jdbc.query("""
                SELECT id, channel, policy_version, timeout_ms,
                       retry_schedule_seconds, finality_mode,
                       webhook_event_names, idempotency_ttl_seconds
                  FROM push_payment_policy
                 WHERE channel=?
                   AND status='ACTIVE'
                   AND now()>=valid_from
                   AND (valid_until IS NULL OR now()<valid_until)
                """, (resultSet, rowNumber) -> map(resultSet), channel.name());
        if (found.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one active push-payment policy for "
                            + channel + " but found " + found.size());
        }
        return found.getFirst();
    }

    public PushPaymentPolicy byId(UUID policyId) {
        List<PushPaymentPolicy> found = jdbc.query("""
                SELECT id, channel, policy_version, timeout_ms,
                       retry_schedule_seconds, finality_mode,
                       webhook_event_names, idempotency_ttl_seconds
                  FROM push_payment_policy
                 WHERE id=?
                """, (resultSet, rowNumber) -> map(resultSet), policyId);
        if (found.size() != 1) {
            throw new IllegalStateException("Push-payment policy does not exist");
        }
        return found.getFirst();
    }

    private PushPaymentPolicy map(java.sql.ResultSet resultSet) {
        try {
            Array sqlArray = resultSet.getArray("retry_schedule_seconds");
            List<Duration> retrySchedule = new ArrayList<>();
            if (sqlArray != null) {
                Object raw = sqlArray.getArray();
                if (raw instanceof Object[] values) {
                    for (Object value : values) {
                        retrySchedule.add(Duration.ofSeconds(
                                ((Number) value).longValue()));
                    }
                }
            }
            Map<String, String> webhookEvents = mapper.readValue(
                    resultSet.getString("webhook_event_names"),
                    new TypeReference<Map<String, String>>() {});
            return new PushPaymentPolicy(
                    resultSet.getObject("id", UUID.class),
                    PaymentChannel.valueOf(resultSet.getString("channel")),
                    resultSet.getInt("policy_version"),
                    Duration.ofMillis(resultSet.getLong("timeout_ms")),
                    List.copyOf(retrySchedule),
                    FinalityMode.valueOf(resultSet.getString("finality_mode")),
                    Map.copyOf(webhookEvents),
                    Duration.ofSeconds(resultSet.getLong("idempotency_ttl_seconds")));
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid push-payment policy", exception);
        }
    }
}
