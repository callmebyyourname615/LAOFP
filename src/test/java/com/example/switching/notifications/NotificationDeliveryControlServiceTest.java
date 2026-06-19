package com.example.switching.notifications;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
class NotificationDeliveryControlServiceTest {
 @Test void backoffIsBounded(){assertEquals(Duration.ofSeconds(30),NotificationDeliveryControlService.backoff(1));assertTrue(NotificationDeliveryControlService.backoff(99).compareTo(Duration.ofHours(1))<=0);}
 @Test void rejectsSecretLikePayloadFields(){assertThrows(IllegalArgumentException.class,()->NotificationDeliveryControlService.assertSafePayload(java.util.Map.of("private_key","x")));}
}
