package com.example.switching.webhook.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.switching.webhook.entity.WebhookRegistrationEntity;

public interface WebhookRegistrationRepository extends JpaRepository<WebhookRegistrationEntity, Long> {

    Optional<WebhookRegistrationEntity> findByWebhookId(String webhookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM WebhookRegistrationEntity r WHERE r.webhookId = :webhookId")
    Optional<WebhookRegistrationEntity> findByWebhookIdForUpdate(@Param("webhookId") String webhookId);

    List<WebhookRegistrationEntity> findByPspIdOrderByCreatedAtDesc(String pspId);

    /** All ACTIVE registrations for a PSP — used by the delivery service to find matching hooks. */
    List<WebhookRegistrationEntity> findByPspIdAndStatus(String pspId, String status);

    /** Increment failedDeliveries and auto-FAIL the registration when threshold is reached. */
    @Modifying
    @Query("""
            UPDATE WebhookRegistrationEntity r
            SET r.failedDeliveries = r.failedDeliveries + 1,
                r.status = CASE WHEN r.failedDeliveries + 1 >= :threshold THEN 'FAILED' ELSE r.status END
            WHERE r.webhookId = :webhookId
            """)
    void incrementFailedDeliveries(@Param("webhookId") String webhookId,
                                   @Param("threshold") int threshold);
}
