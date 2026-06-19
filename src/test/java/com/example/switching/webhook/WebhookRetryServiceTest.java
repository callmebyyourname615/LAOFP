package com.example.switching.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.switching.maintenance.service.SchedulerLockService;
import com.example.switching.webhook.entity.WebhookDeliveryLogEntity;
import com.example.switching.webhook.repository.WebhookDeliveryLogRepository;
import com.example.switching.webhook.service.WebhookDeliveryService;
import com.example.switching.webhook.service.WebhookRetryService;

@ExtendWith(MockitoExtension.class)
class WebhookRetryServiceTest {

    private static final int MAX_ATTEMPTS = 5;

    @Mock WebhookDeliveryLogRepository deliveryLogRepository;
    @Mock WebhookDeliveryService deliveryService;
    @Mock SchedulerLockService lockService;

    private WebhookRetryService retryService;

    @BeforeEach
    void setUp() {
        retryService = new WebhookRetryService(deliveryLogRepository, deliveryService, lockService);
    }

    @Test
    void retryPendingDeliveries_lockNotAcquired_doesNothing() {
        when(lockService.acquire("WEBHOOK_RETRY", 2)).thenReturn(false);

        retryService.retryPendingDeliveries();

        verify(deliveryLogRepository, never()).findPendingForRetry(any(), any(Integer.class));
        verify(deliveryService, never()).retryDelivery(any());
        verify(lockService, never()).release(any());
    }

    @Test
    void retryPendingDeliveries_noPending_releasesLock() {
        when(lockService.acquire("WEBHOOK_RETRY", 2)).thenReturn(true);
        when(deliveryLogRepository.findPendingForRetry(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(List.of());

        retryService.retryPendingDeliveries();

        verify(deliveryService, never()).retryDelivery(any());
        verify(lockService).release("WEBHOOK_RETRY");
    }

    @Test
    void retryPendingDeliveries_retriesAllPendingAndReleasesLock() {
        WebhookDeliveryLogEntity first = entry("wh-1");
        WebhookDeliveryLogEntity second = entry("wh-2");

        when(lockService.acquire("WEBHOOK_RETRY", 2)).thenReturn(true);
        when(deliveryLogRepository.findPendingForRetry(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(List.of(first, second));

        retryService.retryPendingDeliveries();

        verify(deliveryService).retryDelivery(first);
        verify(deliveryService).retryDelivery(second);
        verify(lockService).release("WEBHOOK_RETRY");
    }

    @Test
    void retryPendingDeliveries_singleRetryException_continuesAndReleasesLock() {
        WebhookDeliveryLogEntity first = entry("wh-1");
        WebhookDeliveryLogEntity second = entry("wh-2");

        when(lockService.acquire("WEBHOOK_RETRY", 2)).thenReturn(true);
        when(deliveryLogRepository.findPendingForRetry(any(), eq(MAX_ATTEMPTS)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("timeout")).when(deliveryService).retryDelivery(first);

        retryService.retryPendingDeliveries();

        verify(deliveryService).retryDelivery(first);
        verify(deliveryService).retryDelivery(second);
        verify(lockService).release("WEBHOOK_RETRY");
    }

    @Test
    void retryPendingDeliveries_repositoryException_stillReleasesLock() {
        when(lockService.acquire("WEBHOOK_RETRY", 2)).thenReturn(true);
        when(deliveryLogRepository.findPendingForRetry(any(), eq(MAX_ATTEMPTS)))
                .thenThrow(new RuntimeException("db down"));

        try {
            retryService.retryPendingDeliveries();
        } catch (RuntimeException ignored) {
            // The scheduler infrastructure logs uncaught failures; the lock must still release.
        }

        verify(lockService, times(1)).release("WEBHOOK_RETRY");
    }

    private static WebhookDeliveryLogEntity entry(String webhookId) {
        WebhookDeliveryLogEntity entity = new WebhookDeliveryLogEntity();
        entity.setWebhookId(webhookId);
        entity.setEventType("TRANSFER.SETTLED");
        entity.setEventRef("TXN-1");
        entity.setPayload("{}");
        return entity;
    }
}
