package com.example.switching.webhook.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.switching.maintenance.service.SchedulerLockService;
import com.example.switching.webhook.entity.WebhookDeliveryLogEntity;
import com.example.switching.webhook.repository.WebhookDeliveryLogRepository;

/**
 * Scheduled retry poller for failed webhook deliveries.
 *
 * <p>Runs every 30 seconds. Picks up {@code PENDING} delivery-log rows whose
 * {@code next_retry_at} is in the past and attempts re-delivery via
 * {@link WebhookDeliveryService}.
 *
 * <p>Guarded by {@link SchedulerLockService} (lock name {@code WEBHOOK_RETRY}, 2 minutes)
 * to prevent duplicate execution across replicas.
 */
@Component
public class WebhookRetryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryService.class);
    private static final String LOCK_NAME = "WEBHOOK_RETRY";

    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookDeliveryService       deliveryService;
    private final SchedulerLockService         lockService;

    public WebhookRetryService(WebhookDeliveryLogRepository deliveryLogRepository,
                               WebhookDeliveryService deliveryService,
                               SchedulerLockService lockService) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.deliveryService       = deliveryService;
        this.lockService           = lockService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void retryPendingDeliveries() {
        if (!lockService.acquire(LOCK_NAME, 2)) {
            log.debug("Webhook retry lock not acquired — another replica running");
            return;
        }
        try {
            List<WebhookDeliveryLogEntity> pending =
                    deliveryLogRepository.findPendingForRetry(
                            LocalDateTime.now(),
                            WebhookDeliveryService.MAX_ATTEMPTS);

            if (pending.isEmpty()) return;

            log.debug("Webhook retry: {} pending deliveries to process", pending.size());
            for (WebhookDeliveryLogEntity entry : pending) {
                try {
                    deliveryService.retryDelivery(entry);
                } catch (Exception ex) {
                    log.warn("Webhook retry failed for logId={}: {}", entry.getId(), ex.getMessage());
                }
            }
        } finally {
            lockService.release(LOCK_NAME);
        }
    }
}
