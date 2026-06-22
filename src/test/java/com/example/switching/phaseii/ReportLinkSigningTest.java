package com.example.switching.phaseii;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.example.switching.reportdelivery.EmailLinkDeliveryService;
class ReportLinkSigningTest {
 @Test void tokenIsDeterministicAndBoundToArtifactAndExpiry(){String secret="01234567890123456789012345678901";String first=EmailLinkDeliveryService.hmac("id-1|100",secret);assertEquals(first,EmailLinkDeliveryService.hmac("id-1|100",secret));assertNotEquals(first,EmailLinkDeliveryService.hmac("id-2|100",secret));assertNotEquals(first,EmailLinkDeliveryService.hmac("id-1|101",secret));}
}
