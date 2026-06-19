package com.example.switching.webhook.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.switching.webhook.entity.WebhookDeliveryLogEntity;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLogEntity, Long> {

    /**
     * Fetch PENDING deliveries whose next_retry_at is null (first attempt) or in the past.
     * Called by {@link com.example.switching.webhook.service.WebhookRetryService}.
     */
    @Query("""
            SELECT d FROM WebhookDeliveryLogEntity d
            WHERE d.status = 'PENDING'
              AND d.attemptCount < :maxAttempts
              AND (d.nextRetryAt IS NULL OR d.nextRetryAt <= :now)
            ORDER BY d.createdAt ASC
            """)
    List<WebhookDeliveryLogEntity> findPendingForRetry(@Param("now") LocalDateTime now,
                                                        @Param("maxAttempts") int maxAttempts);

    List<WebhookDeliveryLogEntity> findByWebhookIdOrderByCreatedAtDesc(String webhookId);

    List<WebhookDeliveryLogEntity> findByEventRefOrderByCreatedAtDesc(String eventRef);
}
